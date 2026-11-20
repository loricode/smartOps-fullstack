package com.smartops.batch.importjob;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.SkipListener;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Último recurso: si una operación aceptada choca en el INSERT con una referencia que otro
 * canal registró entre la validación y la escritura (carrera con el API), el ítem se omite
 * y queda como rechazo DUPLICATE_REFERENCE en lugar de tumbar el lote.
 */
public class ImportSkipListener implements SkipListener<OperationLine, ImportOutcome> {

    private static final Logger log = LoggerFactory.getLogger(ImportSkipListener.class);

    private final JdbcTemplate jdbc;
    private final long batchFileId;

    public ImportSkipListener(JdbcTemplate jdbc, long batchFileId) {
        this.jdbc = jdbc;
        this.batchFileId = batchFileId;
    }

    @Override
    public void onSkipInWrite(ImportOutcome item, Throwable t) {
        if (item instanceof ImportOutcome.Accepted accepted) {
            log.warn("Línea {} omitida en escritura: {}", item.lineNumber(), t.getMessage());
            jdbc.update("""
                    INSERT INTO smartops.rejected_record (batch_file_id, line_number, reference, reason_code, reason_detail)
                    VALUES (?, ?, ?, 'DUPLICATE_REFERENCE', 'La referencia fue registrada por otro canal durante el lote')
                    """, batchFileId, item.lineNumber(), accepted.transaction().getReference());
        }
    }
}
