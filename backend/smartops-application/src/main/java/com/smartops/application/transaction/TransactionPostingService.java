package com.smartops.application.transaction;

import com.smartops.application.port.out.AccountRepository;
import com.smartops.application.port.out.AuditTrail;
import com.smartops.application.port.out.AuditTrail.AuditEntry;
import com.smartops.application.port.out.TransactionRepository;
import com.smartops.domain.account.BankAccount;
import com.smartops.domain.transaction.LedgerPostingService;
import com.smartops.domain.transaction.Transaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Aplica contablemente una operación VALIDATED. Lo usan tanto el canal API
 * como el paso de contabilización del job batch, garantizando la misma lógica.
 * Debe ejecutarse dentro de una transacción existente (la del caso de uso o la del chunk).
 */
@Service
public class TransactionPostingService {

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final LedgerPostingService ledger;
    private final AuditTrail audit;
    private final Clock clock;

    public TransactionPostingService(AccountRepository accounts, TransactionRepository transactions,
                                     LedgerPostingService ledger, AuditTrail audit, Clock clock) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.ledger = ledger;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public LedgerPostingService.Outcome post(Transaction tx, String actor) {
        List<String> involved = new ArrayList<>(2);
        tx.getSourceAccount().ifPresent(involved::add);
        tx.getTargetAccount().ifPresent(involved::add);

        Map<String, BankAccount> locked = accounts.lockForPosting(involved);
        BankAccount source = tx.getSourceAccount().map(locked::get).orElse(null);
        BankAccount target = tx.getTargetAccount().map(locked::get).orElse(null);

        LedgerPostingService.Outcome outcome = ledger.post(tx, source, target, clock.instant());
        if (outcome == LedgerPostingService.Outcome.POSTED) {
            if (source != null) {
                accounts.saveBalance(source);
            }
            if (target != null) {
                accounts.saveBalance(target);
            }
        }
        transactions.save(tx);
        audit.record(AuditEntry.of("TRANSACTION", tx.getId(),
                outcome == LedgerPostingService.Outcome.POSTED ? "POSTED" : "REJECTED", actor,
                outcome == LedgerPostingService.Outcome.POSTED
                        ? Map.of("reference", tx.getReference(), "amount", tx.getAmount())
                        : Map.of("reference", tx.getReference(), "reason", tx.getRejectionReason())));
        return outcome;
    }
}
