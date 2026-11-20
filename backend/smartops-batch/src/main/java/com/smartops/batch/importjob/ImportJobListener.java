package com.smartops.batch.importjob;

import com.smartops.application.port.out.AuditTrail;
import com.smartops.application.port.out.AuditTrail.AuditEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

/** Actualiza el resumen de negocio (smartops.batch_execution) y deja rastro de auditoría. */
public class ImportJobListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(ImportJobListener.class);

    private final JdbcTemplate jdbc;
    private final AuditTrail audit;

    public ImportJobListener(JdbcTemplate jdbc, AuditTrail audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Override
    public void beforeJob(JobExecution execution) {
        jdbc.update("UPDATE smartops.batch_execution SET job_execution_id = ? WHERE id = ?",
                execution.getId(), executionId(execution));
    }

    @Override
    public void afterJob(JobExecution execution) {
        long executionId = executionId(execution);
        long fileId = execution.getJobParameters().getLong("batchFileId");
        String actor = execution.getJobParameters().getString("actor");
        boolean ok = execution.getStatus() == BatchStatus.COMPLETED;

        Map<String, Object> c = jdbc.queryForMap("""
                SELECT (SELECT count(*) FROM smartops.financial_transaction WHERE batch_file_id = ?) AS accepted,
                       (SELECT count(*) FROM smartops.rejected_record WHERE batch_file_id = ?) AS rejected_lines,
                       (SELECT count(*) FROM smartops.financial_transaction
                         WHERE batch_file_id = ? AND status = 'REJECTED') AS rejected_posting,
                       (SELECT count(*) FROM smartops.financial_transaction
                         WHERE batch_file_id = ? AND status IN ('PROCESSED','RECONCILED')) AS processed
                """, fileId, fileId, fileId, fileId);
        long accepted = ((Number) c.get("accepted")).longValue();
        long rejectedLines = ((Number) c.get("rejected_lines")).longValue();
        long rejectedPosting = ((Number) c.get("rejected_posting")).longValue();
        long processed = ((Number) c.get("processed")).longValue();
        long durationMs = duration(execution);
        String message = ok ? null : execution.getAllFailureExceptions().stream()
                .map(Throwable::getMessage).collect(Collectors.joining(" | "));

        jdbc.update("""
                UPDATE smartops.batch_execution
                   SET status = ?, read_count = ?, accepted_count = ?, rejected_count = ?, processed_count = ?,
                       finished_at = now(), duration_ms = ?, exit_message = left(?, 1000)
                 WHERE id = ?
                """, ok ? "COMPLETED" : "FAILED", accepted + rejectedLines, accepted - rejectedPosting,
                rejectedLines + rejectedPosting, processed, durationMs, message, executionId);

        audit.record(AuditEntry.of("BATCH_EXECUTION", executionId, ok ? "COMPLETED" : "FAILED", actor,
                Map.of("batchFileId", fileId, "read", accepted + rejectedLines, "processed", processed,
                        "rejected", rejectedLines + rejectedPosting, "durationMs", durationMs)));
        log.info("Lote {} terminado en {} ms: estado={}, procesadas={}, rechazadas={}",
                executionId, durationMs, execution.getStatus(), processed, rejectedLines + rejectedPosting);
    }

    private static long executionId(JobExecution execution) {
        return execution.getJobParameters().getLong("batchExecutionId");
    }

    static long duration(JobExecution execution) {
        LocalDateTime start = execution.getStartTime();
        LocalDateTime end = execution.getEndTime() != null ? execution.getEndTime() : LocalDateTime.now();
        return start == null ? 0 : Duration.between(start, end).toMillis();
    }
}
