package com.smartops.application.port.out;

import com.smartops.domain.transaction.Transaction;

import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository {

    Optional<Transaction> findById(UUID id);

    Optional<Transaction> findByReference(String reference);

    /**
     * Persiste la operación. Si otra operación con la misma referencia se confirmó
     * primero (carrera entre dos peticiones idénticas), lanza {@link DuplicateReferenceException}.
     */
    Transaction save(Transaction transaction);

    class DuplicateReferenceException extends RuntimeException {
        public DuplicateReferenceException(String reference, Throwable cause) {
            super("Ya existe una operación con referencia " + reference, cause);
        }
    }
}
