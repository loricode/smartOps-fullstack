package com.smartops.infrastructure.persistence.jpa;

import com.smartops.application.port.out.TransactionRepository;
import com.smartops.domain.transaction.Channel;
import com.smartops.domain.transaction.Transaction;
import com.smartops.domain.transaction.TransactionStatus;
import com.smartops.domain.transaction.TransactionType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador JPA. Traduce entre el agregado de dominio y la entidad de persistencia.
 * Solo los campos mutables del ciclo de vida se actualizan tras el alta.
 */
@Repository
class TransactionRepositoryAdapter implements TransactionRepository {

    private final TransactionJpaRepository jpa;

    TransactionRepositoryAdapter(TransactionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Transaction> findById(UUID id) {
        return jpa.findById(id).map(TransactionRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<Transaction> findByReference(String reference) {
        return jpa.findByReference(reference).map(TransactionRepositoryAdapter::toDomain);
    }

    @Override
    public Transaction save(Transaction tx) {
        Optional<TransactionEntity> existing = jpa.findById(tx.getId());
        if (existing.isPresent()) {
            TransactionEntity entity = existing.get();
            entity.setStatus(tx.getStatus().name());
            entity.setRejectionReason(tx.getRejectionReason());
            entity.setProcessedAt(tx.getProcessedAt());
            return toDomain(jpa.save(entity));
        }
        try {
            // flush inmediato: la violación de UNIQUE(reference) aflora aquí y no al commit
            return toDomain(jpa.saveAndFlush(toNewEntity(tx)));
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateReferenceException(tx.getReference(), e);
        }
    }

    private static TransactionEntity toNewEntity(Transaction tx) {
        TransactionEntity e = new TransactionEntity();
        e.setId(tx.getId());
        e.setReference(tx.getReference());
        e.setTypeCode(tx.getType().name());
        e.setSourceAccount(tx.getSourceAccount().orElse(null));
        e.setTargetAccount(tx.getTargetAccount().orElse(null));
        e.setAmount(tx.getAmount());
        e.setCurrency(tx.getCurrency());
        e.setValueDate(tx.getValueDate());
        e.setDescription(tx.getDescription());
        e.setStatus(tx.getStatus().name());
        e.setChannel(tx.getChannel().name());
        e.setBatchFileId(tx.getBatchFileId());
        e.setPayloadHash(tx.getPayloadHash());
        e.setRejectionReason(tx.getRejectionReason());
        e.setCreatedBy(tx.getCreatedBy());
        e.setCreatedAt(tx.getCreatedAt());
        e.setProcessedAt(tx.getProcessedAt());
        return e;
    }

    static Transaction toDomain(TransactionEntity e) {
        return new Transaction(e.getId(), e.getReference(), TransactionType.valueOf(e.getTypeCode()),
                e.getSourceAccount(), e.getTargetAccount(), e.getAmount(), e.getCurrency(), e.getValueDate(),
                e.getDescription(), TransactionStatus.valueOf(e.getStatus()), Channel.valueOf(e.getChannel()),
                e.getBatchFileId(), e.getPayloadHash(), e.getRejectionReason(), e.getCreatedBy(),
                e.getCreatedAt(), e.getProcessedAt(), e.getVersion());
    }
}
