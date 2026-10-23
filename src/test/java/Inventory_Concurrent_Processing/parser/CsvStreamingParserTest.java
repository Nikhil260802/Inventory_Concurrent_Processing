package Inventory_Concurrent_Processing.parser;

import Inventory_Concurrent_Processing.exception.CsvParseException;
import Inventory_Concurrent_Processing.model.csv.CsvRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvStreamingParserTest {

    private CsvStreamingParser parser;

    @BeforeEach
    void setUp() {
        parser = new CsvStreamingParser();
    }

    @Test
    @DisplayName("Should successfully stream CSV rows and handle RFC 4180 quotes and commas")
    void testStreamingAndQuoting() throws IOException {
        String csv = """
                code,name,batch_number,quantity,price,expiry_date
                MED-001,"Paracetamol, 500mg Extra",B101,100,12.50,2027-12-31
                MED-002,Amoxicillin,B102,200,45.00,2026-06-30
                """;

        InputStream is = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        try (Stream<CsvRecord> stream = parser.stream(is)) {
            List<CsvRecord> records = stream.toList();

            assertThat(records).hasSize(2);

            CsvRecord r1 = records.get(0);
            assertThat(r1.getRowNumber()).isEqualTo(1L);
            assertThat(r1.getCode()).isEqualTo("MED-001");
            assertThat(r1.getName()).isEqualTo("Paracetamol, 500mg Extra"); // Quoted with comma
            assertThat(r1.getBatchNumber()).isEqualTo("B101");
            assertThat(r1.getQuantity()).isEqualTo("100");
            assertThat(r1.getPrice()).isEqualTo("12.50");
            assertThat(r1.getExpiryDate()).isEqualTo("2027-12-31");

            CsvRecord r2 = records.get(1);
            assertThat(r2.getRowNumber()).isEqualTo(2L);
            assertThat(r2.getCode()).isEqualTo("MED-002");
        }
    }

    @Test
    @DisplayName("Should handle columns in arbitrary order from providers")
    void testColumnOrderIndependence() throws IOException {
        String csv = """
                expiry_date,price,batch_number,code,name,quantity
                2027-12-31,15.75,B505,MED-999,Vitamin D,400
                """;

        InputStream is = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        try (Stream<CsvRecord> stream = parser.stream(is)) {
            List<CsvRecord> records = stream.toList();
            assertThat(records).hasSize(1);

            CsvRecord r = records.get(0);
            assertThat(r.getCode()).isEqualTo("MED-999");
            assertThat(r.getName()).isEqualTo("Vitamin D");
            assertThat(r.getBatchNumber()).isEqualTo("B505");
            assertThat(r.getQuantity()).isEqualTo("400");
            assertThat(r.getPrice()).isEqualTo("15.75");
            assertThat(r.getExpiryDate()).isEqualTo("2027-12-31");
        }
    }

    @Test
    @DisplayName("Should throw CsvParseException when required column is missing in header")
    void testMissingRequiredColumn() {
        String csv = """
                code,name,quantity,price,expiry_date
                MED-001,Paracetamol,100,12.50,2027-12-31
                """;

        InputStream is = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> parser.stream(is))
                .isInstanceOf(CsvParseException.class)
                .hasMessageContaining("Missing required columns: [batch_number]");
    }

    @Test
    @DisplayName("Should throw CsvParseException on empty file")
    void testEmptyFile() {
        InputStream is = new ByteArrayInputStream(new byte[0]);

        assertThatThrownBy(() -> parser.stream(is))
                .isInstanceOf(CsvParseException.class)
                .hasMessageContaining("File is empty or has no header row");
    }
}
