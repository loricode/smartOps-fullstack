package com.smartops.batch.reconciliation;

import com.smartops.domain.reconciliation.ReconciliationMatcher;
import com.smartops.domain.reconciliation.ReconciliationMatcher.Entry;
import com.smartops.domain.reconciliation.ReconciliationResult;
import com.smartops.domain.reconciliation.ReconciliationSummary;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Compara el extracto (staging) con las operaciones procesadas en el mismo rango de fechas.
 * <p>
 * El emparejamiento lo hace PostgreSQL (FULL OUTER JOIN por referencia, con índices) y la
 * <b>clasificación</b> la hace el dominio ({@link ReconciliationMatcher}), de modo que la regla
 * de negocio (tolerancia, moneda) vive en un único lugar probado unitariamente.
 * Los resultados se leen en streaming y se insertan por lotes.
 */
public class CompareTasklet implements Tasklet {

    private static final int FLUSH_SIZE = 1000;

    private static final String JOIN_SQL = """
            WITH internal AS (
                SELECT id, reference, amount, currency FROM smartops.financial_transaction
                 WHERE status IN ('PROCESSED','RECONCILED') AND value_date BETWEEN ? AND ?
            ), external AS (
                SELECT reference, amount, currency FROM smartops.statement_record WHERE batch_file_id = ?
            )
            SELECT i.id, COALESCE(i.reference, e.reference) AS reference,
                   i.amount AS i_amount, i.currency AS i_currency,
                   e.amount AS e_amount, e.currency AS e_currency
              FROM internal i FULL OUTER JOIN external e ON e.reference = i.reference
            """;

    private static final String INSERT_DETAIL = """
            INSERT INTO smartops.reconciliation_detail
                (reconciliation_id, reference, result, internal_amount, external_amount, difference, transaction_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbc;
    private final ReconciliationMatcher matcher;
    private final long statementFileId;
    private final long reconciliationId;

    public CompareTasklet(JdbcTemplate jdbc, ReconciliationMatcher matcher, long statementFileId,
                          long reconciliationId) {
        this.jdbc = jdbc;
        this.matcher = matcher;
        this.statementFileId = statementFileId;
        this.reconciliationId = reconciliationId;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        Map<String, Object> range = jdbc.queryForMap(
                "SELECT min(value_date) AS d_from, max(value_date) AS d_to FROM smartops.statement_record WHERE batch_file_id = ?",
                statementFileId);
        Date from = (Date) range.get("d_from");
        Date to = (Date) range.get("d_to");
        ReconciliationSummary summary = new ReconciliationSummary();

        if (from != null) {
            List<Object[]> details = new ArrayList<>(FLUSH_SIZE);
            List<Object[]> matchedIds = new ArrayList<>(FLUSH_SIZE);
            jdbc.query(JOIN_SQL, (RowCallbackHandler) rs -> {
                Entry internal = entry(rs.getBigDecimal("i_amount"), rs.getString("i_currency"));
                Entry external = entry(rs.getBigDecimal("e_amount"), rs.getString("e_currency"));
                ReconciliationResult result = matcher.classify(internal, external);
                summary.add(result);
                UUID txId = rs.getObject("id", UUID.class);
                details.add(new Object[]{reconciliationId, rs.getString("reference"), result.name(),
                        internal == null ? null : internal.amount(), external == null ? null : external.amount(),
                        ReconciliationMatcher.difference(internal, external), txId});
                if (result == ReconciliationResult.MATCHED) {
                    matchedIds.add(new Object[]{txId});
                }
                if (details.size() >= FLUSH_SIZE) {
                    flush(details, matchedIds);
                }
                contribution.incrementWriteCount(1);
            }, from, to, statementFileId);
            flush(details, matchedIds);
        }

        jdbc.update("""
                UPDATE smartops.reconciliation
                   SET value_date_from = ?, value_date_to = ?, total_internal = ?, total_external = ?,
                       matched_count = ?, amount_mismatch_count = ?, missing_external_count = ?,
                       missing_internal_count = ?
                 WHERE id = ?
                """, from, to, summary.totalInternal(), summary.totalExternal(),
                summary.count(ReconciliationResult.MATCHED), summary.count(ReconciliationResult.AMOUNT_MISMATCH),
                summary.count(ReconciliationResult.MISSING_EXTERNAL),
                summary.count(ReconciliationResult.MISSING_INTERNAL), reconciliationId);
        chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext()
                .putInt("matched", summary.count(ReconciliationResult.MATCHED));
        return RepeatStatus.FINISHED;
    }

    private void flush(List<Object[]> details, List<Object[]> matchedIds) {
        if (!details.isEmpty()) {
            jdbc.batchUpdate(INSERT_DETAIL, details);
            details.clear();
        }
        if (!matchedIds.isEmpty()) {
            // La guarda "status = 'PROCESSED'" respeta la máquina de estados: PROCESSED → RECONCILED
            jdbc.batchUpdate("""
                    UPDATE smartops.financial_transaction
                       SET status = 'RECONCILED', updated_at = now(), version = version + 1
                     WHERE id = ? AND status = 'PROCESSED'
                    """, matchedIds);
            matchedIds.clear();
        }
    }

    private static Entry entry(BigDecimal amount, String currency) {
        return amount == null ? null : new Entry(amount, currency);
    }
}
