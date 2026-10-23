package Inventory_Concurrent_Processing.service.processing;

import Inventory_Concurrent_Processing.dto.ProcessingReportDto;
import Inventory_Concurrent_Processing.entity.ProcessingError;
import Inventory_Concurrent_Processing.entity.ProcessingJob;
import Inventory_Concurrent_Processing.enums.JobStatus;
import Inventory_Concurrent_Processing.enums.ProcessingMode;
import Inventory_Concurrent_Processing.repository.ProcessingErrorRepository;
import Inventory_Concurrent_Processing.repository.ProcessingJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;

/**
 * High-level orchestration service for inventory CSV upload jobs.
 * Coordinates job lifecycle and delegates execution to the chosen processing engine.
 */
@Service
public class InventoryProcessingService {

    private static final Logger log = LoggerFactory.getLogger(InventoryProcessingService.class);

    private final ProcessingJobRepository jobRepository;
    private final ProcessingErrorRepository errorRepository;
    private final ProcessingEngine sequentialProcessingEngine;
    private final ProcessingEngine parallelProcessingEngine;

    public InventoryProcessingService(
            ProcessingJobRepository jobRepository,
            ProcessingErrorRepository errorRepository,
            @Qualifier("sequentialProcessingService") ProcessingEngine sequentialProcessingEngine,
            @Qualifier("parallelProcessingService") ProcessingEngine parallelProcessingEngine) {
        this.jobRepository = jobRepository;
        this.errorRepository = errorRepository;
        this.sequentialProcessingEngine = sequentialProcessingEngine;
        this.parallelProcessingEngine = parallelProcessingEngine;
    }

    /**
     * Creates and saves a new {@link ProcessingJob} in PENDING state.
     *
     * @param fileName original file name
     * @param mode     SEQUENTIAL or PARALLEL
     * @return the saved job entity
     */
    @Transactional
    public ProcessingJob createJob(String fileName, ProcessingMode mode) {
        ProcessingJob job = ProcessingJob.builder()
                .fileName(fileName)
                .processingMode(mode)
                .status(JobStatus.PENDING)
                .totalRecords(0L)
                .successfulRecords(0L)
                .failedRecords(0L)
                .duplicateRecords(0L)
                .build();
        return jobRepository.save(job);
    }

    /**
     * Executes the job synchronously using the chosen strategy.
     *
     * @param jobId       ID of the job
     * @param inputStream byte stream of the CSV
     * @param mode        SEQUENTIAL or PARALLEL
     * @return summary report DTO
     */
    public ProcessingReportDto executeJob(Long jobId, InputStream inputStream, ProcessingMode mode) {
        if (mode == ProcessingMode.PARALLEL) {
            return parallelProcessingEngine.process(jobId, inputStream);
        } else {
            return sequentialProcessingEngine.process(jobId, inputStream);
        }
    }

    /**
     * Retrieves the current status and metrics of a job.
     */
    @Transactional(readOnly = true)
    public ProcessingReportDto getJobReport(Long jobId) {
        ProcessingJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        return ProcessingReportDto.builder()
                .jobId(job.getId())
                .fileName(job.getFileName())
                .processingMode(job.getProcessingMode())
                .status(job.getStatus())
                .startedAt(job.getStartedAt())
                .finishedAt(job.getFinishedAt())
                .totalRecords(job.getTotalRecords())
                .successfulRecords(job.getSuccessfulRecords())
                .failedRecords(job.getFailedRecords())
                .duplicateRecords(job.getDuplicateRecords())
                .executionTimeMs(job.getExecutionTimeMs())
                .throughputRecordsPerSec(job.getThroughputRecordsPerSec())
                .peakMemoryMb(job.getPeakMemoryMb())
                .errorMessage(job.getErrorMessage())
                .build();
    }

    /**
     * Retrieves paginated row-level errors for a job.
     */
    @Transactional(readOnly = true)
    public Page<ProcessingError> getJobErrors(Long jobId, Pageable pageable) {
        return errorRepository.findByJobIdOrderByRowNumberAsc(jobId, pageable);
    }
}
