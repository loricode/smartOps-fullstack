package com.smartops.application.transaction;

import com.smartops.application.port.out.TransactionRepository;
import com.smartops.application.port.out.TransactionRepository.DuplicateReferenceException;
import com.smartops.domain.transaction.Transaction;
import com.smartops.domain.transaction.TransactionDraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Caso de uso: registrar una operación por el canal API de forma idempotente.
 *
 * <ol>
 *   <li>Si la referencia ya existe con el mismo contenido → se devuelve la original (replay).</li>
 *   <li>Si existe con contenido distinto → {@link IdempotencyConflictException}.</li>
 *   <li>Si dos peticiones idénticas llegan a la vez, la restricción UNIQUE de la base de datos
 *       hace fallar a la segunda, que se resuelve como replay de la primera.</li>
 * </ol>
 */
@Service
public class TransactionRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(TransactionRegistrationService.class);

    private final TransactionRepository transactions;
    private final TransactionRegistrar registrar;

    public TransactionRegistrationService(TransactionRepository transactions, TransactionRegistrar registrar) {
        this.transactions = transactions;
        this.registrar = registrar;
    }

    public RegistrationResult register(TransactionDraft draft, String actor) {
        Optional<RegistrationResult> replay = replayIfExists(draft);
        if (replay.isPresent()) {
            return replay.get();
        }
        try {
            return new RegistrationResult(registrar.registerNew(draft, actor), false);
        } catch (DuplicateReferenceException race) {
            log.info("Carrera de idempotencia resuelta para referencia {}", draft.reference());
            return replayIfExists(draft).orElseThrow(() -> race);
        }
    }

    public Transaction get(UUID id) {
        return transactions.findById(id).orElseThrow(() -> new TransactionNotFoundException(id));
    }

    private Optional<RegistrationResult> replayIfExists(TransactionDraft draft) {
        if (draft.reference() == null) {
            return Optional.empty();
        }
        return transactions.findByReference(draft.reference()).map(existing -> {
            if (!existing.getPayloadHash().equals(draft.payloadHash())) {
                throw new IdempotencyConflictException(draft.reference());
            }
            return new RegistrationResult(existing, true);
        });
    }
}
