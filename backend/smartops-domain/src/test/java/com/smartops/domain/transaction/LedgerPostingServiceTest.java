package com.smartops.domain.transaction;

import com.smartops.domain.account.AccountStatus;
import com.smartops.domain.account.BankAccount;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerPostingServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-19T15:00:00Z");
    private final LedgerPostingService ledger = new LedgerPostingService();

    private static BankAccount account(String number, String balance, AccountStatus status) {
        return new BankAccount(1L, number, "Demo", "COP", new BigDecimal(balance), status, 0L);
    }

    private static Transaction validated(TransactionType type, String source, String target, String amount) {
        Transaction tx = Transaction.receive(new TransactionDraft("REF-0001", type, source, target,
                new BigDecimal(amount), "COP", LocalDate.of(2026, 11, 19), null), Channel.API, null, "op", NOW);
        tx.markValidated();
        return tx;
    }

    @Test
    void transferMovesMoneyBetweenAccounts() {
        BankAccount source = account("A1", "1000.00", AccountStatus.ACTIVE);
        BankAccount target = account("A2", "0.00", AccountStatus.ACTIVE);
        Transaction tx = validated(TransactionType.TRANSFER, "A1", "A2", "400.00");

        LedgerPostingService.Outcome outcome = ledger.post(tx, source, target, NOW);

        assertThat(outcome).isEqualTo(LedgerPostingService.Outcome.POSTED);
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.PROCESSED);
        assertThat(source.getBalance()).isEqualByComparingTo("600.00");
        assertThat(target.getBalance()).isEqualByComparingTo("400.00");
    }

    @Test
    void insufficientFundsRejectsWithoutTouchingBalances() {
        BankAccount source = account("A1", "100.00", AccountStatus.ACTIVE);
        BankAccount target = account("A2", "0.00", AccountStatus.ACTIVE);
        Transaction tx = validated(TransactionType.TRANSFER, "A1", "A2", "400.00");

        LedgerPostingService.Outcome outcome = ledger.post(tx, source, target, NOW);

        assertThat(outcome).isEqualTo(LedgerPostingService.Outcome.REJECTED);
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.REJECTED);
        assertThat(tx.getRejectionReason()).startsWith("INSUFFICIENT_FUNDS");
        assertThat(source.getBalance()).isEqualByComparingTo("100.00");
        assertThat(target.getBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void collectionOnlyCreditsTarget() {
        BankAccount target = account("A2", "10.00", AccountStatus.ACTIVE);
        Transaction tx = validated(TransactionType.COLLECTION, null, "A2", "5.00");

        ledger.post(tx, null, target, NOW);

        assertThat(target.getBalance()).isEqualByComparingTo("15.00");
    }

    @Test
    void accountBlockedAfterValidationIsRejectedAtPostingTime() {
        BankAccount source = account("A1", "1000.00", AccountStatus.BLOCKED);
        Transaction tx = validated(TransactionType.PAYMENT, "A1", null, "5.00");

        assertThat(ledger.post(tx, source, null, NOW)).isEqualTo(LedgerPostingService.Outcome.REJECTED);
        assertThat(tx.getRejectionReason()).startsWith("ACCOUNT_BLOCKED");
    }
}
