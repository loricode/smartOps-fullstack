package com.smartops.domain.transaction;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionStatusTest {

    @ParameterizedTest(name = "{0} -> {1} permitido={2}")
    @CsvSource({
            "RECEIVED,   VALIDATED,  true",
            "RECEIVED,   REJECTED,   true",
            "RECEIVED,   PROCESSED,  false",
            "VALIDATED,  PROCESSING, true",
            "PROCESSING, PROCESSED,  true",
            "PROCESSING, REJECTED,   true",
            "PROCESSED,  RECONCILED, true",
            "PROCESSED,  REJECTED,   false",
            "REJECTED,   VALIDATED,  false",
            "RECONCILED, PROCESSED,  false"
    })
    void enforcesLifecycle(TransactionStatus from, TransactionStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }

    @Test
    void rejectedAndReconciledAreFinal() {
        assertThat(TransactionStatus.REJECTED.isFinal()).isTrue();
        assertThat(TransactionStatus.RECONCILED.isFinal()).isTrue();
        assertThat(TransactionStatus.PROCESSED.isFinal()).isFalse();
    }
}
