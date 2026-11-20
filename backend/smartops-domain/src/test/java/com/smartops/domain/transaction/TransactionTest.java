package com.smartops.domain.transaction;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionTest {

    private static final Instant NOW = Instant.parse("2026-11-19T15:00:00Z");

    static TransactionDraft transfer(String ref, String amount) {
        return new TransactionDraft(ref, TransactionType.TRANSFER, "ACC0000001", "ACC0000002",
                new BigDecimal(amount), "COP", LocalDate.of(2026, 11, 19), "Prueba");
    }

    @Test
    void receivedTransactionFollowsHappyPath() {
        Transaction tx = Transaction.receive(transfer("REF-0001", "100.00"), Channel.API, null, "operator", NOW);

        tx.markValidated();
        tx.startProcessing();
        tx.markProcessed(NOW);
        tx.markReconciled();

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.RECONCILED);
        assertThat(tx.getProcessedAt()).isEqualTo(NOW);
    }

    @Test
    void cannotProcessTwice() {
        Transaction tx = Transaction.receive(transfer("REF-0001", "100.00"), Channel.API, null, "operator", NOW);
        tx.markValidated();
        tx.startProcessing();
        tx.markProcessed(NOW);

        assertThatThrownBy(tx::startProcessing)
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("PROCESSED");
    }

    @Test
    void rejectionKeepsReason() {
        Transaction tx = Transaction.receive(transfer("REF-0001", "100.00"), Channel.BATCH, 7L, "batch", NOW);
        tx.reject("ACCOUNT_NOT_FOUND: no existe");

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.REJECTED);
        assertThat(tx.getRejectionReason()).startsWith("ACCOUNT_NOT_FOUND");
        assertThat(tx.getBatchFileId()).isEqualTo(7L);
    }

    @Test
    void payloadHashIgnoresTrailingZerosAndWhitespaceButDetectsChanges() {
        String base = transfer("REF-0001", "100.00").payloadHash();

        assertThat(transfer(" REF-0001 ", "100").payloadHash()).isEqualTo(base);
        assertThat(transfer("REF-0001", "100.01").payloadHash()).isNotEqualTo(base);
    }
}
