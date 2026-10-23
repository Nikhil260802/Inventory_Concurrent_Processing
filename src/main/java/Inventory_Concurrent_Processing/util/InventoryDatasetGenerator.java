package Inventory_Concurrent_Processing.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * <h2>Inventory Dataset Generator</h2>
 *
 * <p>Generates realistic CSV datasets containing 10,000+ inventory records with:
 * <ul>
 *   <li>Valid medicine inventory records</li>
 *   <li>Intentional duplicate records (same code + batch_number)</li>
 *   <li>Intentional validation errors (negative quantity, zero/negative price, invalid dates, blank fields)</li>
 * </ul>
 */
public class InventoryDatasetGenerator {

    private static final String[] MEDICINE_NAMES = {
            "Paracetamol 500mg", "Amoxicillin 250mg", "Ibuprofen 400mg", "Aspirin 100mg",
            "Cough Syrup 100ml", "Vitamin C 500mg", "Cetirizine 10mg", "Omeprazole 20mg",
            "Metformin 500mg", "Atorvastatin 10mg", "Azithromycin 500mg", "Losartan 50mg",
            "Ciprofloxacin 500mg", "Pantoprazole 40mg", "Doxycycline 100mg"
    };

    /**
     * Generates a CSV dataset file with the specified number of records.
     *
     * @param totalRecords number of records (e.g. 10,000)
     * @param outputFile   destination file
     * @throws IOException on write failure
     */
    public static void generateCsv(int totalRecords, File outputFile) throws IOException {
        if (outputFile.getParentFile() != null && !outputFile.getParentFile().exists()) {
            outputFile.getParentFile().mkdirs();
        }

        Random random = new Random(42); // fixed seed for reproducibility across runs
        List<String[]> duplicatePool = new ArrayList<>();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile, StandardCharsets.UTF_8))) {
            // Write RFC 4180 CSV Header
            writer.write("code,name,batch_number,quantity,price,expiry_date");
            writer.newLine();

            LocalDate baseDate = LocalDate.of(2027, 1, 1);

            for (int i = 1; i <= totalRecords; i++) {
                int errorRoll = random.nextInt(100);

                // 1. In-file Duplicate (~4% probability if pool has items)
                if (errorRoll < 4 && !duplicatePool.isEmpty()) {
                    String[] dup = duplicatePool.get(random.nextInt(duplicatePool.size()));
                    writer.write(String.format("%s,%s,%s,%d,%.2f,%s",
                            dup[0], dup[1], dup[2],
                            random.nextInt(100) + 1,
                            Double.parseDouble(dup[3]),
                            dup[4]));
                    writer.newLine();
                    continue;
                }

                // Normal record parameters
                int medIndex = random.nextInt(MEDICINE_NAMES.length);
                String name = MEDICINE_NAMES[medIndex];
                String code = String.format("MED-%05d", medIndex + 1);
                String batchNumber = String.format("BATCH-%04d", (i % 2000) + 1);
                int quantity = random.nextInt(500) + 1;
                double price = 5.0 + (random.nextDouble() * 95.0);
                LocalDate expiryDate = baseDate.plusDays(random.nextInt(730));

                // 2. Intentional validation errors (~6% total)
                if (errorRoll == 4 || errorRoll == 5) {
                    // Invalid negative quantity
                    quantity = -(random.nextInt(50) + 1);
                } else if (errorRoll == 6 || errorRoll == 7) {
                    // Invalid zero/negative price
                    price = 0.0;
                } else if (errorRoll == 8) {
                    // Invalid date format
                    writer.write(String.format("%s,%s,%s,%d,%.2f,invalid-date-format",
                            code, name, batchNumber, quantity, price));
                    writer.newLine();
                    continue;
                } else if (errorRoll == 9) {
                    // Missing / blank mandatory field
                    writer.write(String.format(",%s,%s,%d,%.2f,%s",
                            name, batchNumber, quantity, price, expiryDate));
                    writer.newLine();
                    continue;
                }

                // Write normal or error record
                writer.write(String.format("%s,%s,%s,%d,%.2f,%s",
                        code, name, batchNumber, quantity, price, expiryDate));
                writer.newLine();

                // Save to duplicate pool for subsequent rows
                if (duplicatePool.size() < 400 && quantity > 0 && price > 0) {
                    duplicatePool.add(new String[]{code, name, batchNumber, String.format("%.2f", price), expiryDate.toString()});
                }
            }
        }
    }

    public static void main(String[] args) throws IOException {
        File file = new File("src/main/resources/benchmark/inventory_10000.csv");
        generateCsv(10_000, file);
        System.out.println("Generated 10,000 inventory records at: " + file.getAbsolutePath());
    }
}
