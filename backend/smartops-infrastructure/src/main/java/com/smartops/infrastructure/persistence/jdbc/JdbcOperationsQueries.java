package com.smartops.infrastructure.persistence.jdbc;

import com.smartops.application.query.OperationsQueries;
import com.smartops.application.query.PageResult;
import com.smartops.application.query.PageResult.PageRequest;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Lado de lectura implementado con SQL explícito (consultas paginadas y agregadas). */
@Component
class JdbcOperationsQueries implements OperationsQueries {

    private final NamedParameterJdbcTemplate jdbc;

    JdbcOperationsQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ operaciones
    private static final RowMapper<TransactionView> TX_MAPPER = (rs, i) -> new TransactionView(
            rs.getObject("id", UUID.class), rs.getString("reference"), rs.getString("type_code"),
            rs.getString("source_account"), rs.getString("target_account"), rs.getBigDecimal("amount"),
            rs.getString("currency"), date(rs, "value_date"), rs.getString("description"),
            rs.getString("status"), rs.getString("channel"), nullableLong(rs, "batch_file_id"),
            rs.getString("rejection_reason"), rs.getString("created_by"), instant(rs, "created_at"),
            instant(rs, "processed_at"));

    @Override
    public PageResult<TransactionView> searchTransactions(TransactionFilter f, PageRequest page) {
        List<String> where = new ArrayList<>();
        MapSqlParameterSource p = new MapSqlParameterSource();
        addEq(where, p, "status", upper(f.status()));
        addEq(where, p, "type_code", upper(f.type()));
        addEq(where, p, "channel", upper(f.channel()));
        if (f.reference() != null && !f.reference().isBlank()) {
            where.add("reference ILIKE :reference");
            p.addValue("reference", f.reference().trim() + "%");
        }
        if (f.batchFileId() != null) {
            where.add("batch_file_id = :batchFileId");
            p.addValue("batchFileId", f.batchFileId());
        }
        if (f.valueDateFrom() != null) {
            where.add("value_date >= :from");
            p.addValue("from", f.valueDateFrom());
        }
        if (f.valueDateTo() != null) {
            where.add("value_date <= :to");
            p.addValue("to", f.valueDateTo());
        }
        String whereSql = where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where);
        return page("SELECT * FROM smartops.financial_transaction" + whereSql + " ORDER BY created_at DESC, id",
                "SELECT count(*) FROM smartops.financial_transaction" + whereSql, p, page, TX_MAPPER);
    }

    // ------------------------------------------------------------------ lotes
    private static final String EXECUTION_SELECT = """
            SELECT e.*, f.file_name FROM smartops.batch_execution e
            JOIN smartops.batch_file f ON f.id = e.batch_file_id
            """;

    private static final RowMapper<BatchExecutionView> EXECUTION_MAPPER = (rs, i) -> new BatchExecutionView(
            rs.getLong("id"), rs.getLong("batch_file_id"), rs.getString("file_name"), rs.getString("job_name"),
            rs.getString("status"), rs.getInt("read_count"), rs.getInt("accepted_count"),
            rs.getInt("rejected_count"), rs.getInt("processed_count"), instant(rs, "started_at"),
            instant(rs, "finished_at"), nullableLong(rs, "duration_ms"), rs.getString("exit_message"),
            rs.getString("started_by"));

    @Override
    public PageResult<BatchExecutionView> listBatchExecutions(PageRequest page) {
        return page(EXECUTION_SELECT + " ORDER BY e.id DESC", "SELECT count(*) FROM smartops.batch_execution",
                new MapSqlParameterSource(), page, EXECUTION_MAPPER);
    }

    @Override
    public Optional<BatchExecutionView> findBatchExecution(long id) {
        return jdbc.query(EXECUTION_SELECT + " WHERE e.id = :id", new MapSqlParameterSource("id", id),
                EXECUTION_MAPPER).stream().findFirst();
    }

    @Override
    public PageResult<RejectedRecordView> listRejectedRecords(long batchFileId, PageRequest page) {
        MapSqlParameterSource p = new MapSqlParameterSource("fileId", batchFileId);
        return page("""
                        SELECT id, line_number, reference, reason_code, reason_detail, raw_line
                          FROM smartops.rejected_record WHERE batch_file_id = :fileId ORDER BY line_number
                        """,
                "SELECT count(*) FROM smartops.rejected_record WHERE batch_file_id = :fileId", p, page,
                (rs, i) -> new RejectedRecordView(rs.getLong("id"), rs.getInt("line_number"),
                        rs.getString("reference"), rs.getString("reason_code"), rs.getString("reason_detail"),
                        rs.getString("raw_line")));
    }

    @Override
    public List<RejectionCount> rejectionsByReason(long batchFileId) {
        return jdbc.query("""
                SELECT reason_code, count(*) AS total FROM (
                    SELECT reason_code FROM smartops.rejected_record WHERE batch_file_id = :fileId
                    UNION ALL
                    SELECT split_part(rejection_reason, ':', 1) FROM smartops.financial_transaction
                     WHERE batch_file_id = :fileId AND status = 'REJECTED'
                ) r GROUP BY reason_code ORDER BY total DESC
                """, new MapSqlParameterSource("fileId", batchFileId),
                (rs, i) -> new RejectionCount(rs.getString("reason_code"), rs.getLong("total")));
    }

    // ------------------------------------------------------------------ conciliaciones
    private static final String RECONCILIATION_SELECT = """
            SELECT r.*, f.file_name FROM smartops.reconciliation r
            JOIN smartops.batch_file f ON f.id = r.statement_file_id
            """;

    private static final RowMapper<ReconciliationView> RECONCILIATION_MAPPER = (rs, i) -> new ReconciliationView(
            rs.getLong("id"), rs.getLong("statement_file_id"), rs.getString("file_name"),
            date(rs, "value_date_from"), date(rs, "value_date_to"), rs.getString("status"),
            rs.getInt("total_internal"), rs.getInt("total_external"), rs.getInt("matched_count"),
            rs.getInt("amount_mismatch_count"), rs.getInt("missing_external_count"),
            rs.getInt("missing_internal_count"), instant(rs, "started_at"), instant(rs, "finished_at"),
            rs.getString("exit_message"), rs.getString("started_by"));

    @Override
    public PageResult<ReconciliationView> listReconciliations(PageRequest page) {
        return page(RECONCILIATION_SELECT + " ORDER BY r.id DESC", "SELECT count(*) FROM smartops.reconciliation",
                new MapSqlParameterSource(), page, RECONCILIATION_MAPPER);
    }

    @Override
    public Optional<ReconciliationView> findReconciliation(long id) {
        return jdbc.query(RECONCILIATION_SELECT + " WHERE r.id = :id", new MapSqlParameterSource("id", id),
                RECONCILIATION_MAPPER).stream().findFirst();
    }

    @Override
    public PageResult<ReconciliationDetailView> listReconciliationDetails(long reconciliationId, String result,
                                                                           PageRequest page) {
        MapSqlParameterSource p = new MapSqlParameterSource("rid", reconciliationId);
        String where = " WHERE reconciliation_id = :rid";
        if (result != null && !result.isBlank()) {
            where += " AND result = :result";
            p.addValue("result", result.trim().toUpperCase());
        }
        return page("SELECT * FROM smartops.reconciliation_detail" + where
                        + " ORDER BY CASE result WHEN 'MATCHED' THEN 1 ELSE 0 END, reference",
                "SELECT count(*) FROM smartops.reconciliation_detail" + where, p, page,
                (rs, i) -> new ReconciliationDetailView(rs.getLong("id"), rs.getString("reference"),
                        rs.getString("result"), rs.getBigDecimal("internal_amount"),
                        rs.getBigDecimal("external_amount"), rs.getBigDecimal("difference"),
                        rs.getObject("transaction_id", UUID.class)));
    }

    // ------------------------------------------------------------------ auditoría
    @Override
    public PageResult<AuditView> searchAudit(String entityType, String entityId, String actor, PageRequest page) {
        List<String> where = new ArrayList<>();
        MapSqlParameterSource p = new MapSqlParameterSource();
        addEq(where, p, "entity_type", upper(entityType));
        addEq(where, p, "entity_id", entityId);
        addEq(where, p, "actor", actor);
        String whereSql = where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where);
        return page("SELECT * FROM smartops.audit_log" + whereSql + " ORDER BY occurred_at DESC, id DESC",
                "SELECT count(*) FROM smartops.audit_log" + whereSql, p, page,
                (rs, i) -> new AuditView(rs.getLong("id"), rs.getString("entity_type"), rs.getString("entity_id"),
                        rs.getString("action"), rs.getString("actor"), rs.getString("correlation_id"),
                        rs.getString("details"), instant(rs, "occurred_at")));
    }

    // ------------------------------------------------------------------ tablero
    @Override
    public DashboardSummary dashboard() {
        MapSqlParameterSource none = new MapSqlParameterSource();
        List<StatusCount> byStatus = jdbc.query("""
                SELECT status, sum(total) AS total, coalesce(sum(total_amount), 0) AS amount
                  FROM smartops.v_transaction_summary GROUP BY status ORDER BY status
                """, none, (rs, i) -> new StatusCount(rs.getString("status"), rs.getLong("total"),
                rs.getBigDecimal("amount")));
        long total = byStatus.stream().mapToLong(StatusCount::total).sum();
        BigDecimal processed = byStatus.stream()
                .filter(s -> s.status().equals("PROCESSED") || s.status().equals("RECONCILED"))
                .map(StatusCount::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        Long executions = jdbc.queryForObject("SELECT count(*) FROM smartops.batch_execution", none, Long.class);
        Long rejected = jdbc.queryForObject("SELECT count(*) FROM smartops.rejected_record", none, Long.class);
        Long reconciliations = jdbc.queryForObject("SELECT count(*) FROM smartops.reconciliation", none, Long.class);
        Double matchRate = jdbc.queryForObject("""
                SELECT coalesce(sum(matched_count)::float / nullif(sum(total_internal + missing_internal_count), 0), 0)
                  FROM smartops.reconciliation WHERE status = 'COMPLETED'
                """, none, Double.class);
        return new DashboardSummary(total, processed, byStatus, executions, rejected, reconciliations,
                matchRate == null ? 0 : matchRate);
    }

    // ------------------------------------------------------------------ utilidades
    private <T> PageResult<T> page(String sql, String countSql, MapSqlParameterSource p, PageRequest page,
                                   RowMapper<T> mapper) {
        Long total = jdbc.queryForObject(countSql, p, Long.class);
        p.addValue("limit", page.size()).addValue("offset", page.offset());
        List<T> content = jdbc.query(sql + " LIMIT :limit OFFSET :offset", p, mapper);
        return new PageResult<>(content, page.page(), page.size(), total == null ? 0 : total);
    }

    private static void addEq(List<String> where, MapSqlParameterSource p, String column, String value) {
        if (value != null && !value.isBlank()) {
            where.add(column + " = :" + column);
            p.addValue(column, value.trim());
        }
    }

    private static String upper(String value) {
        return value == null ? null : value.toUpperCase();
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }

    private static LocalDate date(ResultSet rs, String column) throws SQLException {
        java.sql.Date d = rs.getDate(column);
        return d == null ? null : d.toLocalDate();
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long v = rs.getLong(column);
        return rs.wasNull() ? null : v;
    }
}
