package com.smartops.infrastructure.persistence.jdbc;

import com.smartops.application.port.out.BatchRegistry;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
class JdbcBatchRegistry implements BatchRegistry {

    private final JdbcTemplate jdbc;

    JdbcBatchRegistry(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Long> findFileByChecksum(FileKind kind, String checksum) {
        return jdbc.query("SELECT id FROM smartops.batch_file WHERE file_kind = ? AND checksum = ?",
                (rs, i) -> rs.getLong(1), kind.name(), checksum).stream().findFirst();
    }

    @Override
    public long registerFile(FileKind kind, String fileName, String checksum, long sizeBytes, String storagePath,
                             String uploadedBy) {
        try {
            return jdbc.queryForObject("""
                    INSERT INTO smartops.batch_file (file_name, file_kind, checksum, size_bytes, storage_path, uploaded_by)
                    VALUES (?, ?, ?, ?, ?, ?) RETURNING id
                    """, Long.class, fileName, kind.name(), checksum, sizeBytes, storagePath, uploadedBy);
        } catch (DuplicateKeyException e) {
            throw new DuplicateFileException(findFileByChecksum(kind, checksum).orElse(null));
        }
    }

    @Override
    public long startBatchExecution(long batchFileId, String jobName, String startedBy) {
        return jdbc.queryForObject("""
                INSERT INTO smartops.batch_execution (batch_file_id, job_name, status, started_by)
                VALUES (?, ?, 'STARTED', ?) RETURNING id
                """, Long.class, batchFileId, jobName, startedBy);
    }

    @Override
    public long startReconciliation(long statementFileId, String startedBy) {
        return jdbc.queryForObject("""
                INSERT INTO smartops.reconciliation (statement_file_id, status, started_by)
                VALUES (?, 'STARTED', ?) RETURNING id
                """, Long.class, statementFileId, startedBy);
    }

    @Override
    public void failBatchExecution(long executionId, String message) {
        jdbc.update("""
                UPDATE smartops.batch_execution
                   SET status = 'FAILED', finished_at = now(), exit_message = left(?, 1000)
                 WHERE id = ?
                """, message, executionId);
    }

    @Override
    public void failReconciliation(long reconciliationId, String message) {
        jdbc.update("""
                UPDATE smartops.reconciliation
                   SET status = 'FAILED', finished_at = now(), exit_message = left(?, 1000)
                 WHERE id = ?
                """, message, reconciliationId);
    }
}
