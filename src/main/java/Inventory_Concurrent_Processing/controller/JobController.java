package Inventory_Concurrent_Processing.controller;

import Inventory_Concurrent_Processing.dto.ProcessingReportDto;
import Inventory_Concurrent_Processing.entity.ProcessingError;
import Inventory_Concurrent_Processing.entity.ProcessingJob;
import Inventory_Concurrent_Processing.enums.ProcessingMode;
import Inventory_Concurrent_Processing.service.processing.InventoryProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * REST API controller for CSV upload jobs and error reporting.
 */
@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    private static final Logger log = LoggerFactory.getLogger(JobController.class);

    private final InventoryProcessingService processingService;

    public JobController(InventoryProcessingService processingService) {
        this.processingService = processingService;
    }

    /**
     * Uploads and processes an inventory CSV file.
     *
     * @param file CSV file containing inventory rows
     * @param mode SEQUENTIAL (default) or PARALLEL
     * @return summary report and performance metrics
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProcessingReportDto> uploadCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "mode", defaultValue = "PARALLEL") ProcessingMode mode) throws IOException {

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file cannot be empty");
        }

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "uploaded_inventory.csv";
        log.info("Received CSV upload: filename={}, size={} bytes, mode={}", originalFilename, file.getSize(), mode);

        // 1. Create Job record in PENDING state
        ProcessingJob job = processingService.createJob(originalFilename, mode);

        // 2. Stream-process the file
        ProcessingReportDto report = processingService.executeJob(job.getId(), file.getInputStream(), mode);

        return ResponseEntity.status(HttpStatus.CREATED).body(report);
    }

    /**
     * Inspects the status and performance metrics of a specific processing job.
     *
     * @param jobId primary key of the job
     * @return job status report
     */
    @GetMapping("/{jobId}")
    public ResponseEntity<ProcessingReportDto> getJobStatus(@PathVariable("jobId") Long jobId) {
        ProcessingReportDto report = processingService.getJobReport(jobId);
        return ResponseEntity.ok(report);
    }

    /**
     * Retrieves paginated validation and duplicate errors with exact row numbers for a job.
     *
     * @param jobId    primary key of the job
     * @param pageable pagination parameters (default page 0, size 50, sorted by rowNumber ascending)
     * @return page of processing error details
     */
    @GetMapping("/{jobId}/errors")
    public ResponseEntity<Page<ProcessingError>> getJobErrors(
            @PathVariable("jobId") Long jobId,
            @PageableDefault(size = 50, sort = "rowNumber", direction = Sort.Direction.ASC) Pageable pageable) {

        Page<ProcessingError> errors = processingService.getJobErrors(jobId, pageable);
        return ResponseEntity.ok(errors);
    }
}
