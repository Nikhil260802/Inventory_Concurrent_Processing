package Inventory_Concurrent_Processing.parser;

import Inventory_Concurrent_Processing.exception.CsvParseException;
import Inventory_Concurrent_Processing.model.csv.CsvHeader;
import Inventory_Concurrent_Processing.model.csv.CsvRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * <h2>Streaming CSV Parser</h2>
 *
 * Converts an {@link InputStream} into a lazy {@link Stream}{@code <CsvRecord>}
 * without loading the entire file into memory.
 *
 * <h3>Design decisions</h3>
 * <ul>
 *   <li><b>Line-by-line reading</b> via {@link BufferedReader} — only one line
 *       lives in memory at any moment, making 10 000+ row files safe.</li>
 *   <li><b>Lazy {@code Stream}</b> backed by a custom {@link Spliterator} — the
 *       caller (sequential or parallel pipeline) controls consumption speed and
 *       can short-circuit or batch records without parser changes.</li>
 *   <li><b>Column-order independence</b> — the header row is parsed first and a
 *       position index map is built. Provider files may reorder columns freely.</li>
 *   <li><b>RFC 4180 quoting</b> handled by {@link CsvLineParser}.</li>
 *   <li><b>Blank lines</b> are silently skipped; they do not increment the row
 *       counter or produce records.</li>
 *   <li><b>I/O errors</b> mid-stream are wrapped in {@link CsvParseException}
 *       with the current line number so the job can record where it failed.</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * try (Stream<CsvRecord> stream = parser.stream(inputStream)) {
 *     stream.forEach(record -> validationService.validate(record));
 * }
 * }</pre>
 *
 * <p><b>Important:</b> The returned stream holds an open {@link BufferedReader}.
 * Always use it in a try-with-resources block or call {@link Stream#close()}.
 */
@Component
public class CsvStreamingParser {

    private static final Logger log = LoggerFactory.getLogger(CsvStreamingParser.class);

    /** Buffer size for the underlying reader (64 KB — tuned for throughput). */
    private static final int BUFFER_SIZE = 64 * 1024;

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Opens the given stream, validates the header row, and returns a lazy
     * {@link Stream} of parsed {@link CsvRecord} objects.
     *
     * <p>The stream <em>must</em> be closed by the caller to release the
     * underlying reader; use it in a try-with-resources block.
     *
     * @param inputStream  raw bytes of the uploaded CSV file
     * @return lazy, closeable stream of records
     * @throws CsvParseException if the header row is missing or invalid
     * @throws IOException       if the stream cannot be opened
     */
    public Stream<CsvRecord> stream(InputStream inputStream) throws IOException {

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8),
                BUFFER_SIZE
        );

        // ── Read and validate header ─────────────────────────────────────────
        String headerLine = reader.readLine(); // line 0
        if (headerLine == null || headerLine.isBlank()) {
            reader.close();
            throw new CsvParseException("File is empty or has no header row", 0);
        }

        Map<String, Integer> columnIndex = parseHeader(headerLine);
        log.debug("CSV header parsed successfully. Column positions: {}", columnIndex);

        // ── Build a lazy Spliterator over the remaining lines ────────────────
        Spliterator<CsvRecord> spliterator = new CsvSpliterator(reader, columnIndex);

        Stream<CsvRecord> stream = StreamSupport.stream(spliterator, /* parallel= */ false);

        // Register the reader as a close handler so try-with-resources works
        return stream.onClose(() -> {
            try {
                reader.close();
            } catch (IOException e) {
                log.warn("Failed to close CSV reader cleanly", e);
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Header validation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Parses the header line and returns a map of {@code columnName → index}.
     *
     * @param headerLine the first line of the CSV file
     * @return map from canonical column name to its 0-based position
     * @throws CsvParseException if any required column is missing
     */
    private Map<String, Integer> parseHeader(String headerLine) {
        List<String> rawColumns = CsvLineParser.split(headerLine);
        Map<String, Integer> index = new HashMap<>();

        for (int i = 0; i < rawColumns.size(); i++) {
            index.put(rawColumns.get(i).toLowerCase(Locale.ROOT), i);
        }

        // Verify every required column is present
        List<String> missing = new ArrayList<>();
        for (String required : CsvHeader.REQUIRED_COLUMNS) {
            if (!index.containsKey(required)) {
                missing.add(required);
            }
        }

        if (!missing.isEmpty()) {
            throw new CsvParseException(
                    "Missing required columns: " + missing + ". Found: " + index.keySet(), 1
            );
        }

        return Collections.unmodifiableMap(index);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Spliterator implementation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * A pull-based {@link Spliterator} that reads one CSV line per
     * {@link #tryAdvance} call — guaranteeing constant memory use regardless
     * of file size.
     */
    private static final class CsvSpliterator implements Spliterator<CsvRecord> {

        private final BufferedReader reader;
        private final Map<String, Integer> columnIndex;

        /** Current row number (1-based; incremented for each non-blank line). */
        private long rowNumber = 0L;

        CsvSpliterator(BufferedReader reader, Map<String, Integer> columnIndex) {
            this.reader      = reader;
            this.columnIndex = columnIndex;
        }

        @Override
        public boolean tryAdvance(java.util.function.Consumer<? super CsvRecord> action) {
            try {
                String line;
                // Skip blank lines without incrementing the counter
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;

                    rowNumber++;
                    CsvRecord record = parseLine(line, rowNumber);
                    action.accept(record);
                    return true;
                }
                return false; // EOF
            } catch (IOException e) {
                throw new CsvParseException("I/O error while reading", rowNumber, e);
            }
        }

        @Override
        public Spliterator<CsvRecord> trySplit() {
            // Intentionally not splittable here — parallelism is handled at
            // the chunk-batching layer in the processing service, not inside
            // the parser. This keeps the reader single-threaded and avoids
            // concurrent reads on the same BufferedReader.
            return null;
        }

        @Override
        public long estimateSize() {
            return Long.MAX_VALUE; // Unknown file length
        }

        @Override
        public int characteristics() {
            return ORDERED | NONNULL;
        }

        // ── Line → CsvRecord ─────────────────────────────────────────────────

        /**
         * Splits a raw CSV line into fields and maps them by column name.
         *
         * @param line      trimmed, non-blank CSV data line
         * @param rowNumber 1-based row counter for error reporting
         * @return a populated {@link CsvRecord}
         * @throws CsvParseException if the row has fewer columns than the header
         */
        private CsvRecord parseLine(String line, long rowNumber) {
            List<String> fields = CsvLineParser.split(line);

            // Guard: row must have at least as many fields as the header
            if (fields.size() < CsvHeader.REQUIRED_COLUMN_COUNT) {
                throw new CsvParseException(
                        "Expected " + CsvHeader.REQUIRED_COLUMN_COUNT
                        + " columns but found " + fields.size(),
                        rowNumber
                );
            }

            String rawLineCapped = line.length() > 2_000 ? line.substring(0, 2_000) : line;

            return CsvRecord.builder()
                    .rowNumber(rowNumber)
                    .code(       fieldAt(fields, CsvHeader.CODE))
                    .name(       fieldAt(fields, CsvHeader.NAME))
                    .batchNumber(fieldAt(fields, CsvHeader.BATCH_NUMBER))
                    .quantity(   fieldAt(fields, CsvHeader.QUANTITY))
                    .price(      fieldAt(fields, CsvHeader.PRICE))
                    .expiryDate( fieldAt(fields, CsvHeader.EXPIRY_DATE))
                    .rawLine(rawLineCapped)
                    .build();
        }

        /**
         * Retrieves a field value using the pre-built column-name → index map.
         *
         * @param fields    parsed fields for the current row
         * @param colName   canonical lowercase column name
         * @return the field string, or empty string when the index is out of bounds
         */
        private String fieldAt(List<String> fields, String colName) {
            Integer idx = columnIndex.get(colName);
            if (idx == null || idx >= fields.size()) return "";
            return fields.get(idx);
        }
    }
}
