package com.smartops.application.transaction;

import com.smartops.application.port.out.AccountRepository;
import com.smartops.application.port.out.AuditTrail;
import com.smartops.application.port.out.AuditTrail.AuditEntry;
import com.smartops.application.port.out.TransactionRepository;
import com.smartops.domain.shared.Violation;
import com.smartops.domain.transaction.Channel;
import com.smartops.domain.transaction.Transaction;
import com.smartops.domain.transaction.TransactionDraft;
import com.smartops.domain.transaction.TransactionPolicy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;

/**
 * Unidad transaccional de registro: recibir → validar → contabilizar, todo o nada.
 * Se separa de {@link TransactionRegistrationService} para que el manejo de la carrera
 * de idempotencia ocurra fuera de la transacción que falló.
 */
@Component
class TransactionRegistrar {

    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final TransactionPolicy policy;
    private final TransactionPostingService posting;
    private final AuditTrail audit;
    private final Clock clock;

    TransactionRegistrar(TransactionRepository transactions, AccountRepository accounts, TransactionPolicy policy,
                         TransactionPostingService posting, AuditTrail audit, Clock clock) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.policy = policy;
        this.posting = posting;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public Transaction registerNew(TransactionDraft draft, String actor) {
        Transaction tx = Transaction.receive(draft, Channel.API, null, actor, clock.instant());
        audit.record(AuditEntry.of("TRANSACTION", tx.getId(), "RECEIVED", actor,
                Map.of("reference", tx.getReference(), "type", tx.getType(), "amount", tx.getAmount())));

        List<Violation> violations = policy.validate(draft, accounts);
        if (!violations.isEmpty()) {
            tx.reject(violations.get(0).asReason());
            Transaction saved = transactions.save(tx);
            audit.record(AuditEntry.of("TRANSACTION", tx.getId(), "REJECTED", actor,
                    Map.of("violations", violations.stream().map(Violation::asReason).toList())));
            return saved;
        }

        tx.markValidated();
        transactions.save(tx); // el INSERT aquí detecta pronto una referencia duplicada
        posting.post(tx, actor);
        return tx;
    }
}
