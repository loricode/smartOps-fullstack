package com.smartops.application.query;

import com.smartops.application.query.PageResult.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lado de lectura (CQRS ligero): modelos de consulta planos, optimizados para
 * pantallas y reportes, sin pasar por los agregados de dominio.
 */
public interface OperationsQueries {

    // ---------- Operaciones ----------
    record TransactionFilter(String status, String type, String channel, String reference,
                             Long batchFileId, LocalDate valueDateFrom, LocalDate valueDateTo) { }

    record TransactionView(UUID id, String reference, String type, String sourceAccount, String targetAccount,
                           BigDecimal amount, String currency, LocalDate valueDate, String description,
                           String status, String channel, Long batchFileId, String rejectionReason,
                           String createdBy, Instant createdAt, Instant processedAt) { }

    PageResult<TransactionView> searchTransactions(TransactionFilter filter, PageRequest page);

    // ---------- Lotes ----------
    record BatchExecutionView(long id, long batchFileId, String fileName, String jobName, String status,
                              int readCount, int acceptedCount, int rejectedCount, int processedCount,
                              Instant startedAt, Instant finishedAt, Long durationMs, String exitMessage,
                              String startedBy) { }

    record RejectedRecordView(long id, int lineNumber, String reference, String reasonCode,
                              String reasonDetail, String rawLine) { }

    record RejectionCount(String reasonCode, long total) { }

    PageResult<BatchExecutionView> listBatchExecutions(PageRequest page);

    Optional<BatchExecutionView> findBatchExecution(long id);

    PageResult<RejectedRecordView> listRejectedRecords(long batchFileId, PageRequest page);

    List<RejectionCount> rejectionsByReason(long batchFileId);

    // ---------- Conciliaciones ----------
    record ReconciliationView(long id, long statementFileId, String fileName, LocalDate valueDateFrom,
                              LocalDate valueDateTo, String status, int totalInternal, int totalExternal,
                              int matchedCount, int amountMismatchCount, int missingExternalCount,
                              int missingInternalCount, Instant startedAt, Instant finishedAt,
                              String exitMessage, String startedBy) { }

    record ReconciliationDetailView(long id, String reference, String result, BigDecimal internalAmount,
                                    BigDecimal externalAmount, BigDecimal difference, UUID transactionId) { }

    PageResult<ReconciliationView> listReconciliations(PageRequest page);

    Optional<ReconciliationView> findReconciliation(long id);

    PageResult<ReconciliationDetailView> listReconciliationDetails(long reconciliationId, String result,
                                                                    PageRequest page);

    // ---------- Auditoría ----------
    record AuditView(long id, String entityType, String entityId, String action, String actor,
                     String correlationId, String details, Instant occurredAt) { }

    PageResult<AuditView> searchAudit(String entityType, String entityId, String actor, PageRequest page);

    // ---------- Tablero ----------
    record StatusCount(String status, long total, BigDecimal amount) { }

    record DashboardSummary(long totalTransactions, BigDecimal processedAmount, List<StatusCount> byStatus,
                            long batchExecutions, long rejectedRecords, long reconciliations,
                            double reconciliationMatchRate) { }

    DashboardSummary dashboard();
}
