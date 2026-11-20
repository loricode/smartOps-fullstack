package com.smartops.batch.reconciliation;

import com.smartops.application.port.out.AuditTrail;
import com.smartops.application.port.out.AuditTrail.AuditEntry;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.stream.Collectors;

public class ReconciliationJobListener implements JobExecutionListener {

    private final JdbcTemplate jdbc;
    private final AuditTrail audit;

    public ReconciliationJobListener(JdbcTemplate jdbc, AuditTrail audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Override
    public void afterJob(JobExecution execution) {
        long reconciliationId = execution.getJobParameters().getLong("reconciliationId");
        String actor = execution.getJobParameters().getString("actor");
        boolean ok = execution.getStatus() == BatchStatus.COMPLETED;
        String message = ok ? null : execution.getAllFailureExceptions().stream()
                .map(Throwable::getMessage).collect(Collectors.joining(" | "));
        jdbc.update("""
                UPDATE smartops.reconciliation SET status = ?, finished_at = now(), exit_message = left(?, 1000)
                 WHERE id = ?
                """, ok ? "COMPLETED" : "FAILED", message, reconciliationId);

        Map<String, Object> counts = jdbc.queryForMap("""
                SELECT matched_count, amount_mismatch_count, missing_external_count, missing_internal_count
                  FROM smartops.reconciliation WHERE id = ?
                """, reconciliationId);
        audit.record(AuditEntry.of("RECONCILIATION", reconciliationId, ok ? "COMPLETED" : "FAILED", actor, counts));
    }
}
