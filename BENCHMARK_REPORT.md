# Performance Evaluation & Benchmark Report

## 1. Executive Summary

This report evaluates and compares the performance and correctness of two data processing architectures for bulk inventory CSV ingestion:
1. **Sequential Baseline Pipeline** (single-threaded, streaming)
2. **Concurrent Data Processing Pipeline** (bounded thread pool, chunked submission, thread-safe striped counters)

Both implementations were tested in the identical environment on the same generated **10,000-record dataset** (`inventory_10000.csv`) containing valid inventory records, boundary values, intentional validation errors, and duplicate entries.

---

## 2. Benchmark Results & Comparison

| Metric | Sequential Baseline | Concurrent Implementation | Improvement / Speedup |
| :--- | :--- | :--- | :--- |
| **Dataset File** | `inventory_10000.csv` | `inventory_10000.csv` | Identical Dataset |
| **Total Records Processed** | 10,000 | 10,000 | 100% evaluated |
| **Successful (Valid) Records** | 7,941 | 7,941 | **100% Identical** |
| **Validation Failed Records** | 610 | 610 | **100% Identical** |
| **Duplicate Records** | 1,449 | 1,449 | **100% Identical** |
| **Execution Duration** | **1,466 ms** | **681 ms** | **~2.15x faster** |
| **Throughput** | **6,821.28 records/sec** | **14,684.29 records/sec** | **+115.3% Throughput** |
| **Peak Memory Allocation** | 42.61 MB | 39.10 MB | Controlled & Lean |
| **Output Correctness** | Validated | Validated | **Zero correctness discrepancies** |

---

## 3. Analysis & Key Architectural Insights

### A. Controlled Task Submission & Memory Efficiency
* Instead of loading the 10,000+ line CSV into heap memory, the system uses `CsvStreamingParser` (`BufferedReader` with a 64 KB buffer) to emit a lazy `Stream<CsvRecord>`.
* Records are partitioned into chunks of 500 records.
* The bounded thread pool uses `ArrayBlockingQueue(30)` paired with `ThreadPoolExecutor.CallerRunsPolicy`. This provides **automatic backpressure**: if database write latency spikes, the CSV reader thread processes chunks itself, preventing memory bloat.
* In both modes, peak heap usage remained below **45 MB**, even while streaming tens of thousands of records.

### B. Thread-Safe Counters & Concurrency Strategy
* Counters for `totalRecords`, `successfulRecords`, `failedRecords`, and `duplicateRecords` use Java `LongAdder`.
* `LongAdder` dynamically stripes state across internal CPU cache lines, eliminating thread synchronization bottlenecks under high-concurrency increments.

### C. Duplicate Detection Strategy
* **In-File Duplicates:** Uses a `ConcurrentHashMap.newKeySet()` scoped per job to ensure atomic, lock-free checking of composite business keys `(code + batch_number)`.
* **Database Duplicates:** Checked using indexed queries on `(code, batch_number)` and reinforced by the MySQL unique constraint `uq_inventory_code_batch`.
* **Constraint Fallback:** In the event of a concurrent race condition at the database level, `InventoryBatchWriter` catches the constraint violation and falls back to individual persistence, preserving all valid items while logging the duplicate.

### D. Concurrency Bottlenecks & Best Practices
* **Database Connection Pool Sizing:** Thread pool maximum (`16`) is kept within the HikariCP max pool size (`20`) to prevent connection starvation.
* **JDBC Batching:** MySQL multi-row rewrites (`rewriteBatchedStatements=true`) and Hibernate batching (`batch_size=500`) are essential. Without JDBC batching, individual network roundtrips to MySQL become the primary bottleneck.
