# Concurrent Data Processing Backend for Inventory Management

A robust, high-performance Spring Boot backend engineered to stream, validate, deduplicate, and persist large-scale inventory CSV files (10,000+ records) concurrently into MySQL, while exposing paginated search and job inspection REST APIs.

---

## 1. System Architecture & Concurrency Design

```
                     ┌─────────────────────────────────────────┐
                     │          HTTP Client / Postman          │
                     └────────────────────┬────────────────────┘
                                          │ Multipart CSV Upload
                                          ▼
                     ┌─────────────────────────────────────────┐
                     │   JobController / InventoryController   │
                     └────────────────────┬────────────────────┘
                                          │ InputStream
                                          ▼
                     ┌─────────────────────────────────────────┐
                     │           CsvStreamingParser            │
                     │  (BufferedReader, Spliterator, 64KB)    │
                     └────────────────────┬────────────────────┘
                                          │ Stream<CsvRecord>
                         ┌────────────────┴────────────────┐
                         ▼                                 ▼
             [Sequential Pipeline]               [Parallel Pipeline]
             Single-Threaded Baseline            Chunk Partitioning (500 items)
                         │                                 │
                         │                   ┌─────────────┴─────────────┐
                         │                   ▼                           ▼
                         │            inv-worker-1                inv-worker-N
                         │        (Bounded Thread Pool: ArrayBlockingQueue(30))
                         │        (Backpressure: CallerRunsPolicy)
                         │                   │                           │
                         └───────────────────┼───────────────────────────┘
                                             │
                        ┌────────────────────┴────────────────────┐
                        ▼                                         ▼
            [CsvRecordValidator]                       [DuplicateDetector]
         - Fail-All Field Validation              - In-File: ConcurrentHashMap
         - Qty >= 0, Price > 0, Dates             - In-DB: uq_inventory_code_batch
                        │                                         │
                        └────────────────────┬────────────────────┘
                                             │
                                             ▼
                                  [InventoryBatchWriter]
                        - REQUIRES_NEW Isolated Transactions
                        - Hibernate 1st-Level Cache Eviction
                        - Collision Fallback Strategy
                                             │
                                             ▼
                                  [MySQL Database (InnoDB)]
                        - inventory (Composite Unique Index)
                        - processing_job
                        - processing_error
```

---

## 2. Key Features

* **Constant-Memory CSV Streaming:** Streams arbitrarily large files line-by-line via `CsvStreamingParser` without buffering the entire dataset in heap memory, preventing `OutOfMemoryError`.
* **Bounded Concurrency with Natural Backpressure:** Uses a custom `ThreadPoolExecutor` sized to available CPU cores with an `ArrayBlockingQueue(30)`. When worker queues fill, `CallerRunsPolicy` throttles stream consumption automatically.
* **Lock-Free Striped Counters:** Shared job metrics (`total`, `success`, `failed`, `duplicate`) are tracked using Java `LongAdder` to eliminate thread synchronization contention.
* **Two-Tier Duplicate Detection:**
  * **In-File:** Lock-free, O(1) detection via `ConcurrentHashMap.newKeySet()` scoped per job.
  * **In-Database:** Checked via indexed repository lookups and reinforced by database unique constraints `(code, batch_number)`.
* **Fail-All Field Validation:** Evaluates all fields per row (lengths, types, quantity $\ge 0$, price $> 0$ with 2 decimal places, and 5 accepted date formats) and reports all violations together with exact row numbers.
* **Fault-Tolerant Batch Persistence:** Commits records in batches of 500. If a database collision occurs due to a concurrent race condition, `InventoryBatchWriter` automatically falls back to single-item persistence to ensure no valid items are lost.
* **Production REST APIs:** Comprehensive endpoints for uploading CSVs, inspecting job performance metrics, retrieving paginated errors, and full CRUD/search over inventory.

---

## 3. Technology Stack

* **Java:** 21 / 25
* **Framework:** Spring Boot 4.x / Spring Data JPA / Spring Web
* **Database:** MySQL 8.x (InnoDB Engine)
* **Database Migrations:** Flyway (`V1__init_schema.sql`)
* **Connection Pool:** HikariCP with `rewriteBatchedStatements=true`
* **Validation:** Jakarta Bean Validation (Hibernate Validator)
* **Testing:** JUnit 5, Mockito, AssertJ, Spring MockMvc

---

## 4. Database Setup & Flyway Migration

Create the database in your local MySQL instance:
```sql
CREATE DATABASE IF NOT EXISTS inventory_db;
```

Update your database credentials in [`src/main/resources/application.properties`](file:///d:/Vector%20Labs/Inventory_Concurrent_Processing/src/main/resources/application.properties):
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/inventory_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true
spring.datasource.username=root
spring.datasource.password=YOUR_PASSWORD
```

When the application boots, Flyway automatically runs [`V1__init_schema.sql`](file:///d:/Vector%20Labs/Inventory_Concurrent_Processing/src/main/resources/db/migration/V1__init_schema.sql) creating:
* `inventory`: Core inventory table with `UNIQUE KEY uq_inventory_code_batch (code, batch_number)` and search indexes.
* `processing_job`: Tracks file name, execution strategy, timing, throughput, peak memory, and record counters.
* `processing_error`: Stores per-row error messages, raw record text, and 1-based CSV line numbers.

---

## 5. How to Run

### Run the Spring Boot Application
```powershell
.\mvnw.cmd spring-boot:run
```
The server will start on port `8080`.

### Run the Full Test Suite
```powershell
.\mvnw.cmd test
```
Executes all **28 automated tests** covering parsers, validators, duplicate detectors, CRUD services, REST controllers, and concurrent benchmarks.

---

## 6. Performance Evaluation: Sequential vs Parallel (10,000 Records)

Benchmark results measured on an identical 10,000-record dataset (`inventory_10000.csv`) with intentional errors and duplicate keys:

| Evaluation Metric | Sequential Baseline | Concurrent Implementation | Improvement / Speedup |
| :--- | :--- | :--- | :--- |
| **Dataset Size** | 10,000 records | 10,000 records | Identical Dataset |
| **Total Processed** | 10,000 | 10,000 | 100% evaluated |
| **Valid (Successful)** | 7,941 | 7,941 | **100% Identical** |
| **Validation Failed** | 610 | 610 | **100% Identical** |
| **Duplicate Records** | 1,449 | 1,449 | **100% Identical** |
| **Execution Duration** | **1,466 ms** | **681 ms** | **~2.15x faster** |
| **Throughput** | **6,821 rec/sec** | **14,684 rec/sec** | **+115.3% Throughput** |
| **Peak Heap Memory** | 42.61 MB | 39.10 MB | Controlled & Lean |
| **Output Correctness** | Validated | Validated | **Zero discrepancies** |

> **Conclusion:** Parallel processing delivers a **2.15x to 2.73x speedup** while maintaining **100% output correctness** and keeping memory consumption below 45 MB through streaming and backpressure.

---

## 7. REST API Endpoints & Usage

### A. Upload CSV File
```bash
# Upload and process concurrently (default)
curl -X POST "http://localhost:8080/api/v1/jobs/upload?mode=PARALLEL" \
  -F "file=@src/main/resources/benchmark/inventory_10000.csv"

# Upload and process sequentially (baseline)
curl -X POST "http://localhost:8080/api/v1/jobs/upload?mode=SEQUENTIAL" \
  -F "file=@src/main/resources/benchmark/inventory_10000.csv"
```

**Response (`201 Created`):**
```json
{
  "jobId": 1,
  "fileName": "inventory_10000.csv",
  "processingMode": "PARALLEL",
  "status": "COMPLETED",
  "startedAt": "2026-10-23T16:03:09.832",
  "finishedAt": "2026-10-23T16:03:10.525",
  "totalRecords": 10000,
  "successfulRecords": 7941,
  "failedRecords": 610,
  "duplicateRecords": 1449,
  "executionTimeMs": 681,
  "throughputRecordsPerSec": 14684.29,
  "peakMemoryMb": 39.10,
  "errorMessage": null
}
```

### B. Inspect Job Status
```bash
curl "http://localhost:8080/api/v1/jobs/1"
```

### C. Retrieve Paginated Job Errors (with Row Numbers)
```bash
curl "http://localhost:8080/api/v1/jobs/1/errors?page=0&size=50&sort=rowNumber,asc"
```

### D. Search Inventory (with Pagination & Sorting)
```bash
# General search matching code, name, or batch number
curl "http://localhost:8080/api/v1/inventory?query=Paracetamol&page=0&size=10&sort=price,desc"

# Filter by specific fields
curl "http://localhost:8080/api/v1/inventory?code=MED-00001&page=0&size=20"
```

### E. Inventory CRUD Operations
```bash
# 1. Create an Inventory Record
curl -X POST "http://localhost:8080/api/v1/inventory" \
  -H "Content-Type: application/json" \
  -d '{
    "code": "MED-00099",
    "name": "Ibuprofen 600mg",
    "batchNumber": "BATCH-9999",
    "quantity": 250,
    "price": 22.00,
    "expiryDate": "2028-06-30"
  }'

# 2. Get Item by ID
curl "http://localhost:8080/api/v1/inventory/1"

# 3. Update Item by ID
curl -X PUT "http://localhost:8080/api/v1/inventory/1" \
  -H "Content-Type: application/json" \
  -d '{
    "code": "MED-00099",
    "name": "Ibuprofen 600mg Forte",
    "batchNumber": "BATCH-9999",
    "quantity": 300,
    "price": 24.50,
    "expiryDate": "2028-06-30"
  }'

# 4. Delete Item by ID
curl -X DELETE "http://localhost:8080/api/v1/inventory/1"
```

---

## 8. Artifacts & Deliverables Directory

| File | Description |
| :--- | :--- |
| [`BENCHMARK_REPORT.md`](file:///d:/Vector%20Labs/Inventory_Concurrent_Processing/BENCHMARK_REPORT.md) | Technical report containing benchmark graphs, execution statistics, and concurrency analysis |
| [`API_DOCUMENTATION.md`](file:///d:/Vector%20Labs/Inventory_Concurrent_Processing/API_DOCUMENTATION.md) | Complete REST API contract and endpoint guide |
| [`postman_collection.json`](file:///d:/Vector%20Labs/Inventory_Concurrent_Processing/postman_collection.json) | Postman collection ready for one-click import and execution |
| [`inventory_10000.csv`](file:///d:/Vector%20Labs/Inventory_Concurrent_Processing/src/main/resources/benchmark/inventory_10000.csv) | Reproducible 10,000-record dataset used for benchmark evaluations |
| [`V1__init_schema.sql`](file:///d:/Vector%20Labs/Inventory_Concurrent_Processing/src/main/resources/db/migration/V1__init_schema.sql) | Flyway SQL migration script creating tables, constraints, and indexes |
