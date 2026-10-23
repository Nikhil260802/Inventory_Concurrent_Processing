-- =============================================================================
-- V1__init_schema.sql
-- Initial database schema for the Inventory Concurrent Processing system.
--
-- Tables:
--   inventory        – validated inventory records (business key: code + batch_number)
--   processing_job   – CSV upload job lifecycle & summary statistics
--   processing_error – per-row validation / duplicate errors for each job
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. inventory
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS inventory (
    id             BIGINT          NOT NULL AUTO_INCREMENT,
    code           VARCHAR(100)    NOT NULL            COMMENT 'Provider-assigned entry / medicine code',
    name           VARCHAR(255)    NOT NULL            COMMENT 'Human-readable item name',
    batch_number   VARCHAR(100)    NOT NULL            COMMENT 'Manufacturing batch / lot number',
    quantity       INT             NOT NULL            COMMENT 'Available stock (>= 0)',
    price          DECIMAL(12, 2)  NOT NULL            COMMENT 'Unit price (> 0)',
    expiry_date    DATE            NOT NULL            COMMENT 'Batch expiration date',
    source_job_id  BIGINT                              COMMENT 'FK to processing_job that inserted this row',
    created_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),

    -- Business-key uniqueness: one active batch per code
    CONSTRAINT uq_inventory_code_batch UNIQUE (code, batch_number)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Validated inventory records loaded from provider CSV files';

-- Search indexes
CREATE INDEX idx_inventory_code         ON inventory (code);
CREATE INDEX idx_inventory_name         ON inventory (name);
CREATE INDEX idx_inventory_batch_number ON inventory (batch_number);
CREATE INDEX idx_inventory_source_job   ON inventory (source_job_id);

-- -----------------------------------------------------------------------------
-- 2. processing_job
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS processing_job (
    id                        BIGINT          NOT NULL AUTO_INCREMENT,
    file_name                 VARCHAR(512)    NOT NULL COMMENT 'Original uploaded file name',
    processing_mode           VARCHAR(20)     NOT NULL COMMENT 'SEQUENTIAL | PARALLEL',
    status                    VARCHAR(20)     NOT NULL DEFAULT 'PENDING'
                                              COMMENT 'PENDING | PROCESSING | COMPLETED | FAILED',

    -- Timing
    created_at                DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    started_at                DATETIME(6)              COMMENT 'When record processing actually began',
    finished_at               DATETIME(6)              COMMENT 'When processing finished (success or failure)',

    -- Record counters
    total_records             BIGINT          NOT NULL DEFAULT 0 COMMENT 'Total CSV data rows read',
    successful_records        BIGINT          NOT NULL DEFAULT 0 COMMENT 'Rows persisted to inventory',
    failed_records            BIGINT          NOT NULL DEFAULT 0 COMMENT 'Rows that failed validation',
    duplicate_records         BIGINT          NOT NULL DEFAULT 0 COMMENT 'Rows already present in DB or earlier in CSV',

    -- Performance metrics
    execution_time_ms         BIGINT                   COMMENT 'Total wall-clock time in milliseconds',
    throughput_records_per_sec DECIMAL(10,2)            COMMENT 'Records evaluated per second',
    peak_memory_mb            DECIMAL(10,2)            COMMENT 'Peak JVM heap usage during job (MB, best-effort)',

    -- Failure info
    error_message             TEXT                     COMMENT 'Top-level error if status = FAILED',

    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'CSV processing job lifecycle and summary statistics';

CREATE INDEX idx_job_status     ON processing_job (status);
CREATE INDEX idx_job_created_at ON processing_job (created_at);

-- -----------------------------------------------------------------------------
-- 3. processing_error
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS processing_error (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    job_id         BIGINT      NOT NULL            COMMENT 'FK to processing_job',
    csv_row_number BIGINT      NOT NULL            COMMENT '1-based row number in the CSV (header = 0)',
    error_type     VARCHAR(50) NOT NULL            COMMENT 'VALIDATION_ERROR | DUPLICATE | PARSE_ERROR',
    raw_record     TEXT                            COMMENT 'Verbatim CSV row content (capped at 2000 chars)',
    error_message  TEXT        NOT NULL            COMMENT 'Human-readable description of the failure',
    created_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),

    CONSTRAINT fk_error_job
        FOREIGN KEY (job_id) REFERENCES processing_job (id)
        ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Per-row validation and processing errors for each CSV job';

CREATE INDEX idx_error_job_id     ON processing_error (job_id);
CREATE INDEX idx_error_row_number ON processing_error (csv_row_number);
