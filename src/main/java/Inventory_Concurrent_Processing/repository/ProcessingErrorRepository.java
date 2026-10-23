package Inventory_Concurrent_Processing.repository;

import Inventory_Concurrent_Processing.entity.ProcessingError;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link ProcessingError}.
 *
 * <p>Error records are written during processing and read when a client
 * requests the error report for a specific job.
 */
@Repository
public interface ProcessingErrorRepository extends JpaRepository<ProcessingError, Long> {

    /**
     * Returns a paginated list of errors for a given job, ordered by row number.
     *
     * @param jobId    the parent job's surrogate key
     * @param pageable pagination and sorting instructions
     * @return page of errors for the job
     */
    Page<ProcessingError> findByJobIdOrderByRowNumberAsc(Long jobId, Pageable pageable);

    /**
     * Counts how many error rows exist for a specific job.
     * Useful to quickly compute remaining error count without loading entities.
     *
     * @param jobId the parent job's surrogate key
     * @return total error count
     */
    long countByJobId(Long jobId);

    /**
     * Counts errors for a job filtered by error type (e.g. "VALIDATION_ERROR",
     * "DUPLICATE", "PARSE_ERROR").
     *
     * @param jobId     the parent job's surrogate key
     * @param errorType the classification label
     * @return count of matching error rows
     */
    @Query("SELECT COUNT(e) FROM ProcessingError e WHERE e.jobId = :jobId AND e.errorType = :errorType")
    long countByJobIdAndErrorType(@Param("jobId") Long jobId, @Param("errorType") String errorType);
}
