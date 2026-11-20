package com.smartops.domain.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Agregado "operación financiera". Las transiciones de estado solo
 * pueden hacerse a través de sus métodos, que aplican {@link TransactionStatus}.
 */
public class Transaction {

    private final UUID id;
    private final String reference;
    private final TransactionType type;
    private final String sourceAccount;
    private final String targetAccount;
    private final BigDecimal amount;
    private final String currency;
    private final LocalDate valueDate;
    private final String description;
    private final Channel channel;
    private final Long batchFileId;
    private final String payloadHash;
    private final String createdBy;
    private final Instant createdAt;
    private TransactionStatus status;
    private String rejectionReason;
    private Instant processedAt;
    private Long version;

    @SuppressWarnings("java:S107") // constructor de reconstitución desde persistencia
    public Transaction(UUID id, String reference, TransactionType type, String sourceAccount,
                       String targetAccount, BigDecimal amount, String currency, LocalDate valueDate,
                       String description, TransactionStatus status, Channel channel, Long batchFileId,
                       String payloadHash, String rejectionReason, String createdBy, Instant createdAt,
                       Instant processedAt, Long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.reference = Objects.requireNonNull(reference, "reference");
        this.type = Objects.requireNonNull(type, "type");
        this.sourceAccount = sourceAccount;
        this.targetAccount = targetAccount;
        this.amount = Objects.requireNonNull(amount, "amount");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.valueDate = Objects.requireNonNull(valueDate, "valueDate");
        this.description = description;
        this.status = Objects.requireNonNull(status, "status");
        this.channel = Objects.requireNonNull(channel, "channel");
        this.batchFileId = batchFileId;
        this.payloadHash = Objects.requireNonNull(payloadHash, "payloadHash");
        this.rejectionReason = rejectionReason;
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.processedAt = processedAt;
        this.version = version;
    }

    /** Crea una operación recién recibida a partir de un borrador ya validado en formato. */
    public static Transaction receive(TransactionDraft draft, Channel channel, Long batchFileId,
                                      String actor, Instant now) {
        return new Transaction(UUID.randomUUID(), draft.reference(), draft.type(), draft.sourceAccount(),
                draft.targetAccount(), draft.amount(), draft.currency(), draft.valueDate(),
                draft.description(), TransactionStatus.RECEIVED, channel, batchFileId,
                draft.payloadHash(), null, actor, now, null, null);
    }

    public void markValidated() {
        transitionTo(TransactionStatus.VALIDATED);
    }

    public void startProcessing() {
        transitionTo(TransactionStatus.PROCESSING);
    }

    public void markProcessed(Instant when) {
        transitionTo(TransactionStatus.PROCESSED);
        this.processedAt = when;
    }

    public void reject(String reason) {
        transitionTo(TransactionStatus.REJECTED);
        this.rejectionReason = reason;
    }

    public void markReconciled() {
        transitionTo(TransactionStatus.RECONCILED);
    }

    private void transitionTo(TransactionStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStateTransitionException(reference, status, target);
        }
        this.status = target;
    }

    public UUID getId() { return id; }
    public String getReference() { return reference; }
    public TransactionType getType() { return type; }
    public Optional<String> getSourceAccount() { return Optional.ofNullable(sourceAccount); }
    public Optional<String> getTargetAccount() { return Optional.ofNullable(targetAccount); }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public LocalDate getValueDate() { return valueDate; }
    public String getDescription() { return description; }
    public TransactionStatus getStatus() { return status; }
    public Channel getChannel() { return channel; }
    public Long getBatchFileId() { return batchFileId; }
    public String getPayloadHash() { return payloadHash; }
    public String getRejectionReason() { return rejectionReason; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getProcessedAt() { return processedAt; }
    public Long getVersion() { return version; }
}
