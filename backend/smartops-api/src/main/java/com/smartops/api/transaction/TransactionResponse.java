package com.smartops.api.transaction;

import com.smartops.domain.transaction.Transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Contrato de salida: desacopla el API del modelo de dominio. */
public record TransactionResponse(UUID id, String reference, String type, String sourceAccount,
                                  String targetAccount, BigDecimal amount, String currency, LocalDate valueDate,
                                  String description, String status, String channel, Long batchFileId,
                                  String rejectionReason, String createdBy, Instant createdAt,
                                  Instant processedAt) {

    public static TransactionResponse from(Transaction tx) {
        return new TransactionResponse(tx.getId(), tx.getReference(), tx.getType().name(),
                tx.getSourceAccount().orElse(null), tx.getTargetAccount().orElse(null), tx.getAmount(),
                tx.getCurrency(), tx.getValueDate(), tx.getDescription(), tx.getStatus().name(),
                tx.getChannel().name(), tx.getBatchFileId(), tx.getRejectionReason(), tx.getCreatedBy(),
                tx.getCreatedAt(), tx.getProcessedAt());
    }
}
