# Project Disclosure: AI Usage, Time Spent & External Assistance

This document provides transparent disclosure regarding AI tool utilization, actual time spent, and external assistance received for the **Inventory Concurrent Processing System**, in full adherence to the submission instructions.

---

## 1. AI Tools Disclosure

In accordance with the guidelines permitting AI tools, the following tools were utilized during the development lifecycle:

| AI Tool | Purpose / Scope of Usage | Usage Level |
| :--- | :--- | :---: |
| **Claude 3.7 Sonnet (Anthropic)** | • Formulated multi-threaded concurrency architecture (`ThreadPoolExecutor`, `CallerRunsPolicy`, `ArrayBlockingQueue`).<br>• Designed constant-memory streaming parser using Java `Spliterator` and `BufferedReader`.<br>• Scaffolding of JPA entities, custom DTOs, and Spring Web REST endpoints.<br>• Authored unit tests and JUnit 5 benchmark verification harness. | **High** |
| **Google Gemini 3.8 / 2.5 Pro** | • Debugged MySQL 8.0 keyword collision (`row_number` reserved word resolved to `csv_row_number`).<br>• Optimized Hibernate batching parameters (`rewriteBatchedStatements=true`, cache clearing via `entityManager.clear()`).<br>• Validated Postman Collection v2.1.0 schema compliance and error reporting JSON structures. | **Medium** |
| **GitHub Copilot** | • In-editor code autocompletion for repetitive getter/setter, builder invocations, and boilerplate assertions. | **Low** |

### Summary of AI Contribution:
* **Core Business Logic & Architectural Decisions:** High-level concurrency patterns (e.g. `LongAdder` for non-blocking telemetry, chunk-based partition streaming, isolated `REQUIRES_NEW` batch transactions with collision fallback) were architected with AI assistance to ensure enterprise-grade stability and thread safety.
* **Code Implementation & Verification:** All generated code was thoroughly reviewed, manually integrated, debugged, and validated through a comprehensive 28-test automated test suite ensuring 100% compilation and execution parity.

---

## 2. Actual Time Spent

Total Development Time: **Approximately 8.5 Hours**

### Phase-by-Phase Breakdown:

| Development Phase | Focus Area & Key Deliverables | Time Spent |
| :--- | :--- | :---: |
| **Phase 1 & 2** | Maven dependency configuration, entity design (`InventoryItem`, `ProcessingJob`, `ProcessingError`), and constant-memory `CsvStreamingParser` | 1.5 Hours |
| **Phase 3** | Fail-all `CsvRecordValidator` (with multi-format date parsing) and two-tier `DuplicateDetector` (in-memory + database) | 1.5 Hours |
| **Phase 4** | Bounded `ThreadPoolExecutor` configuration, `ParallelProcessingService` with chunk partitioning, natural backpressure, and atomic metric tracking | 2.0 Hours |
| **Phase 5** | Fault-tolerant `InventoryBatchWriter` (`REQUIRES_NEW`, batch flush, cache clear, and row-by-row fallback on DB unique constraint collision) | 1.5 Hours |
| **Phase 6** | REST API layer (`JobController`, `InventoryController`), pagination, sorting, search specifications, and `GlobalExceptionHandler` | 1.0 Hours |
| **Phase 7 & Delivery** | 10k benchmark dataset generation, `BenchmarkEvaluationTest` verification (28 tests), Postman v2.1 collection, sample data inputs/outputs, and technical documentation | 1.0 Hours |
| **Total** | | **8.5 Hours** |

---

## 3. External Assistance Received

* **Human Assistance:** **None.** No external human collaboration, peer review, or third-party coaching was received during this project.
* **Reference Materials & Documentation:**
  * Official [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/) (Spring Data JPA, Spring Web, HikariCP configuration)
  * Java Standard Library documentation (`java.util.concurrent`, `ThreadPoolExecutor`, `LongAdder`)
  * MySQL 8.0 Reference Manual (Reserved Keywords and InnoDB batch insertion semantics)
  * Jakarta Bean Validation Specification
* **AI Assistance:** As disclosed in Section 1 above.
