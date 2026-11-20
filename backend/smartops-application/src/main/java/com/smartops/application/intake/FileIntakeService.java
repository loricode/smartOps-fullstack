package com.smartops.application.intake;

import com.smartops.application.port.out.AuditTrail;
import com.smartops.application.port.out.AuditTrail.AuditEntry;
import com.smartops.application.port.out.BatchJobLauncher;
import com.smartops.application.port.out.BatchRegistry;
import com.smartops.application.port.out.BatchRegistry.DuplicateFileException;
import com.smartops.application.port.out.BatchRegistry.FileKind;
import com.smartops.application.port.out.FileStorage;
import com.smartops.application.port.out.FileStorage.StoredFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

/**
 * Casos de uso de recepción de archivos: guarda el archivo, aplica idempotencia
 * por checksum, registra la ejecución y lanza el job de forma asíncrona.
 * <p>
 * No es transaccional a propósito: las filas deben estar confirmadas antes de
 * que el job (en otro hilo) las lea.
 */
@Service
public class FileIntakeService {

    private static final Logger log = LoggerFactory.getLogger(FileIntakeService.class);

    private final FileStorage storage;
    private final BatchRegistry registry;
    private final BatchJobLauncher launcher;
    private final AuditTrail audit;

    public FileIntakeService(FileStorage storage, BatchRegistry registry, BatchJobLauncher launcher,
                             AuditTrail audit) {
        this.storage = storage;
        this.registry = registry;
        this.launcher = launcher;
        this.audit = audit;
    }

    public record IntakeResult(long fileId, long executionId) { }

    /** Archivo de operaciones a cargar y contabilizar. */
    public IntakeResult submitOperationsFile(String fileName, InputStream content, String actor) {
        StoredFile stored = storeUnique(FileKind.OPERATIONS, fileName, content, actor);
        long fileId = registerOrDiscard(FileKind.OPERATIONS, fileName, stored, actor);
        long executionId = registry.startBatchExecution(fileId, BatchJobLauncher.IMPORT_JOB, actor);
        audit.record(AuditEntry.of("BATCH_FILE", fileId, "RECEIVED", actor,
                Map.of("fileName", fileName, "sizeBytes", stored.sizeBytes(), "executionId", executionId)));
        try {
            launcher.launchOperationsImport(fileId, executionId, stored.path().toString(), actor);
        } catch (RuntimeException e) {
            registry.failBatchExecution(executionId, "No se pudo lanzar el job: " + e.getMessage());
            throw e;
        }
        return new IntakeResult(fileId, executionId);
    }

    /** Extracto externo contra el que se concilian las operaciones procesadas. */
    public IntakeResult submitStatementFile(String fileName, InputStream content, String actor) {
        StoredFile stored = storeUnique(FileKind.STATEMENT, fileName, content, actor);
        long fileId = registerOrDiscard(FileKind.STATEMENT, fileName, stored, actor);
        long reconciliationId = registry.startReconciliation(fileId, actor);
        audit.record(AuditEntry.of("RECONCILIATION", reconciliationId, "STARTED", actor,
                Map.of("fileName", fileName, "statementFileId", fileId)));
        try {
            launcher.launchReconciliation(fileId, reconciliationId, stored.path().toString(), actor);
        } catch (RuntimeException e) {
            registry.failReconciliation(reconciliationId, "No se pudo lanzar el job: " + e.getMessage());
            throw e;
        }
        return new IntakeResult(fileId, reconciliationId);
    }

    private StoredFile storeUnique(FileKind kind, String fileName, InputStream content, String actor) {
        StoredFile stored = storage.store(fileName, content);
        Optional<Long> existing = registry.findFileByChecksum(kind, stored.checksum());
        if (existing.isPresent()) {
            storage.discard(stored.path());
            log.warn("Archivo duplicado {} rechazado (ya recibido con id {})", fileName, existing.get());
            audit.record(AuditEntry.of("BATCH_FILE", existing.get(), "DUPLICATE_REJECTED", actor,
                    Map.of("fileName", fileName, "checksum", stored.checksum())));
            throw new DuplicateFileException(existing.get());
        }
        return stored;
    }

    private long registerOrDiscard(FileKind kind, String fileName, StoredFile stored, String actor) {
        try {
            return registry.registerFile(kind, fileName, stored.checksum(), stored.sizeBytes(),
                    stored.path().toString(), actor);
        } catch (DuplicateFileException race) {
            storage.discard(stored.path());
            throw race;
        }
    }
}
