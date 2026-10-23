package Inventory_Concurrent_Processing.repository;

import Inventory_Concurrent_Processing.entity.ProcessingJob;
import Inventory_Concurrent_Processing.enums.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link ProcessingJob}.
 */
@Repository
public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, Long> {

    /**
     * Returns all jobs with a specific status, sorted by creation time descending.
     *
     * @param status   the lifecycle status to filter on
     * @param pageable pagination / sorting instructions
     * @return page of matching jobs
     */
    Page<ProcessingJob> findByStatus(JobStatus status, Pageable pageable);

    /**
     * Bulk-updates the status of a single job. Used by the service layer to
     * mark a job FAILED without loading and re-saving the full entity.
     *
     * @param jobId     the job to update
     * @param newStatus the target status
     * @return number of rows affected (0 or 1)
     */
    @Modifying
    @Query("UPDATE ProcessingJob j SET j.status = :status WHERE j.id = :jobId")
    int updateStatus(@Param("jobId") Long jobId, @Param("status") JobStatus status);

    /**
     * Finds all jobs that are currently in PROCESSING state.
     * Useful for recovery / health-check logic on application startup.
     *
     * @return list of stuck PROCESSING jobs
     */
    List<ProcessingJob> findByStatus(JobStatus status);
}
