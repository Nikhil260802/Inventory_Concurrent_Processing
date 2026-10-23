package Inventory_Concurrent_Processing.service.processing;

import Inventory_Concurrent_Processing.dto.ProcessingReportDto;

import java.io.InputStream;

/**
 * Common contract for CSV processing implementations (Sequential Baseline and Parallel).
 */
public interface ProcessingEngine {

    /**
     * Executes the processing pipeline for the given job.
     *
     * @param jobId       surrogate key of the pre-created {@link Inventory_Concurrent_Processing.entity.ProcessingJob}
     * @param inputStream open byte stream of the uploaded CSV file
     * @return summary report containing counts and performance metrics
     */
    ProcessingReportDto process(Long jobId, InputStream inputStream);
}
