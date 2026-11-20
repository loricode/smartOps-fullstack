package com.smartops.batch.importjob;

import com.smartops.batch.importjob.ImportOutcome.Accepted;
import com.smartops.batch.importjob.ImportOutcome.Rejected;
import com.smartops.domain.transaction.Transaction;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Escribe un chunk completo con dos sentencias JDBC por lotes (aceptadas y rechazadas),
 * dentro de la misma transacción del chunk.
 */
public class ImportOutcomeWriter implements ItemWriter<ImportOutcome> {

    private static final String INSERT_TX = """
            INSERT INTO smartops.financial_transaction
                (id, reference, type_code, source_account, target_account, amount, currency, value_date,
                 description, status, channel, batch_file_id, payload_hash, created_by, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String INSERT_REJECTED = """
            INSERT INTO smartops.rejected_record
                (batch_file_id, line_number, reference, reason_code, reason_detail, raw_line)
            VALUES (?, ?, ?, ?, left(?, 500), ?)
            """;

    private final JdbcTemplate jdbc;
    private final long batchFileId;

    public ImportOutcomeWriter(JdbcTemplate jdbc, long batchFileId) {
        this.jdbc = jdbc;
        this.batchFileId = batchFileId;
    }

    @Override
    public void write(Chunk<? extends ImportOutcome> chunk) {
        List<Object[]> accepted = new ArrayList<>();
        List<Object[]> rejected = new ArrayList<>();
        for (ImportOutcome outcome : chunk) {
            if (outcome instanceof Accepted a) {
                accepted.add(txRow(a.transaction()));
            } else if (outcome instanceof Rejected r) {
                rejected.add(new Object[]{batchFileId, r.lineNumber(), truncate(r.reference(), 64), r.reasonCode(),
                        r.reasonDetail(), r.rawLine()});
            }
        }
        if (!accepted.isEmpty()) {
            jdbc.batchUpdate(INSERT_TX, accepted);
        }
        if (!rejected.isEmpty()) {
            jdbc.batchUpdate(INSERT_REJECTED, rejected);
        }
    }

    private static Object[] txRow(Transaction tx) {
        return new Object[]{tx.getId(), tx.getReference(), tx.getType().name(), tx.getSourceAccount().orElse(null),
                tx.getTargetAccount().orElse(null), tx.getAmount(), tx.getCurrency(), tx.getValueDate(),
                truncate(tx.getDescription(), 255), tx.getStatus().name(), tx.getChannel().name(),
                tx.getBatchFileId(), tx.getPayloadHash(), tx.getCreatedBy(), Timestamp.from(tx.getCreatedAt())};
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
