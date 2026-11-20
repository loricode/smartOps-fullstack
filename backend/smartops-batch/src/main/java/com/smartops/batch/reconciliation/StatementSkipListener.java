package com.smartops.batch.reconciliation;

import org.springframework.batch.core.SkipListener;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * En este job los registros inválidos se manejan con el mecanismo de "skip" de Spring Batch
 * (a diferencia del job de importación, donde el rechazo es un resultado del procesador).
 * Ambos enfoques son válidos; el skip es más simple cuando el registro inválido no aporta datos.
 */
public class StatementSkipListener implements SkipListener<StatementLine, StatementLine> {

    private static final String INSERT = """
            INSERT INTO smartops.rejected_record (batch_file_id, line_number, reference, reason_code, reason_detail, raw_line)
            VALUES (?, ?, ?, ?, left(?, 500), ?)
            """;

    private final JdbcTemplate jdbc;
    private final long batchFileId;

    public StatementSkipListener(JdbcTemplate jdbc, long batchFileId) {
        this.jdbc = jdbc;
        this.batchFileId = batchFileId;
    }

    @Override
    public void onSkipInRead(Throwable t) {
        if (t instanceof FlatFileParseException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            jdbc.update(INSERT, batchFileId, e.getLineNumber(), null, "MALFORMED_RECORD",
                    cause.getClass().getSimpleName() + ": " + cause.getMessage(), e.getInput());
        }
    }

    @Override
    public void onSkipInProcess(StatementLine item, Throwable t) {
        jdbc.update(INSERT, batchFileId, item.lineNumber(), item.reference(), "DUPLICATE_REFERENCE",
                t.getMessage(), null);
    }
}
