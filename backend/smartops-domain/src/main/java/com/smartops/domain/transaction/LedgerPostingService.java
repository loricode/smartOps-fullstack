package com.smartops.domain.transaction;

import com.smartops.domain.account.BankAccount;
import com.smartops.domain.shared.DomainException;

import java.time.Instant;

/**
 * Aplica una operación validada sobre los saldos de las cuentas.
 * Si el débito falla (saldo insuficiente, cuenta bloqueada) la operación queda
 * RECHAZADA y ningún saldo se modifica: el débito se ejecuta antes que el crédito.
 */
public class LedgerPostingService {

    public enum Outcome { POSTED, REJECTED }

    public Outcome post(Transaction tx, BankAccount source, BankAccount target, Instant now) {
        tx.startProcessing();
        try {
            if (tx.getType().requiresSource()) {
                requireMatching(source, tx.getSourceAccount().orElse(null));
                source.debit(tx.getAmount());
            }
            if (tx.getType().requiresTarget()) {
                requireMatching(target, tx.getTargetAccount().orElse(null));
                target.credit(tx.getAmount());
            }
        } catch (DomainException e) {
            tx.reject(e.getCode() + ": " + e.getMessage());
            return Outcome.REJECTED;
        }
        tx.markProcessed(now);
        return Outcome.POSTED;
    }

    private static void requireMatching(BankAccount account, String expectedNumber) {
        if (account == null || !account.getAccountNumber().equals(expectedNumber)) {
            throw new IllegalArgumentException("Cuenta " + expectedNumber + " no suministrada para el asiento");
        }
    }
}
