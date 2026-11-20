package com.smartops.application.port.out;

import java.util.Optional;

/**
 * Registro de archivos y ejecuciones. Cada método confirma de forma independiente
 * para que los jobs asíncronos vean las filas creadas antes de su lanzamiento.
 */
public interface BatchRegistry {

    enum FileKind { OPERATIONS, STATEMENT }

    Optional<Long> findFileByChecksum(FileKind kind, String checksum);

    /** @throws DuplicateFileException si el mismo contenido ya fue recibido. */
    long registerFile(FileKind kind, String fileName, String checksum, long sizeBytes, String storagePath,
                      String uploadedBy);

    long startBatchExecution(long batchFileId, String jobName, String startedBy);

    long startReconciliation(long statementFileId, String startedBy);

    void failBatchExecution(long executionId, String message);

    void failReconciliation(long reconciliationId, String message);

    class DuplicateFileException extends RuntimeException {
        private final Long existingFileId;

        public DuplicateFileException(Long existingFileId) {
            super("El archivo ya fue recibido previamente (id " + existingFileId + ")");
            this.existingFileId = existingFileId;
        }

        public Long getExistingFileId() {
            return existingFileId;
        }
    }
}
