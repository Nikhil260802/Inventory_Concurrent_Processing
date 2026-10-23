package Inventory_Concurrent_Processing.dto;

import Inventory_Concurrent_Processing.enums.JobStatus;
import Inventory_Concurrent_Processing.enums.ProcessingMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data Transfer Object representing the execution summary and performance metrics
 * of a CSV processing job.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessingReportDto {

    private Long jobId;
    private String fileName;
    private ProcessingMode processingMode;
    private JobStatus status;

    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    private Long totalRecords;
    private Long successfulRecords;
    private Long failedRecords;
    private Long duplicateRecords;

    private Long executionTimeMs;
    private BigDecimal throughputRecordsPerSec;
    private BigDecimal peakMemoryMb;

    private String errorMessage;
}
