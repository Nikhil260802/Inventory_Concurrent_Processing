package Inventory_Concurrent_Processing.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * Low-level RFC 4180–compliant CSV line splitter.
 *
 * <p>Handles:
 * <ul>
 *   <li>Plain comma-delimited fields</li>
 *   <li>Double-quoted fields that may contain commas, newlines, or escaped
 *       double-quotes ({@code ""})</li>
 *   <li>Leading/trailing whitespace outside quotes (trimmed)</li>
 * </ul>
 *
 * <p>This class is <em>stateless</em> and therefore safe to share across
 * threads without synchronisation.
 */
public final class CsvLineParser {

    private static final char COMMA  = ',';
    private static final char QUOTE  = '"';

    private CsvLineParser() { /* utility */ }

    /**
     * Splits a single CSV line into its individual field values.
     *
     * @param line one raw line from the CSV file (must not be {@code null})
     * @return list of unescaped, trimmed field strings; never {@code null}
     */
    public static List<String> split(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        int len = line.length();

        for (int i = 0; i < len; i++) {
            char ch = line.charAt(i);

            if (inQuotes) {
                if (ch == QUOTE) {
                    // Peek ahead: "" → escaped literal quote
                    if (i + 1 < len && line.charAt(i + 1) == QUOTE) {
                        current.append(QUOTE);
                        i++; // skip second quote
                    } else {
                        inQuotes = false; // closing quote
                    }
                } else {
                    current.append(ch);
                }
            } else {
                if (ch == QUOTE) {
                    inQuotes = true;
                } else if (ch == COMMA) {
                    fields.add(current.toString().trim());
                    current.setLength(0);
                } else {
                    current.append(ch);
                }
            }
        }

        // Last field (no trailing comma required)
        fields.add(current.toString().trim());
        return fields;
    }
}
