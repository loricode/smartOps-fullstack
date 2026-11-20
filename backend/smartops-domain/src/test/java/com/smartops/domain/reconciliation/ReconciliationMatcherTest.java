package com.smartops.domain.reconciliation;

import com.smartops.domain.reconciliation.ReconciliationMatcher.Entry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReconciliationMatcherTest {

    private final ReconciliationMatcher exact = new ReconciliationMatcher(BigDecimal.ZERO);

    private static Entry cop(String amount) {
        return new Entry(new BigDecimal(amount), "COP");
    }

    @Test
    void classifiesAllFourOutcomes() {
        assertThat(exact.classify(cop("100.00"), cop("100"))).isEqualTo(ReconciliationResult.MATCHED);
        assertThat(exact.classify(cop("100.00"), cop("99.99"))).isEqualTo(ReconciliationResult.AMOUNT_MISMATCH);
        assertThat(exact.classify(cop("100.00"), null)).isEqualTo(ReconciliationResult.MISSING_EXTERNAL);
        assertThat(exact.classify(null, cop("100.00"))).isEqualTo(ReconciliationResult.MISSING_INTERNAL);
    }

    @Test
    void currencyDifferenceIsAMismatch() {
        assertThat(exact.classify(cop("100"), new Entry(new BigDecimal("100"), "USD")))
                .isEqualTo(ReconciliationResult.AMOUNT_MISMATCH);
    }

    @Test
    void toleranceAbsorbsRoundingDifferences() {
        ReconciliationMatcher tolerant = new ReconciliationMatcher(new BigDecimal("0.01"));
        assertThat(tolerant.classify(cop("100.00"), cop("99.99"))).isEqualTo(ReconciliationResult.MATCHED);
        assertThat(tolerant.classify(cop("100.00"), cop("99.98"))).isEqualTo(ReconciliationResult.AMOUNT_MISMATCH);
    }

    @Test
    void differenceAndSummary() {
        assertThat(ReconciliationMatcher.difference(cop("100"), cop("90"))).isEqualByComparingTo("10");
        assertThat(ReconciliationMatcher.difference(cop("100"), null)).isNull();

        ReconciliationSummary summary = new ReconciliationSummary();
        summary.add(ReconciliationResult.MATCHED);
        summary.add(ReconciliationResult.MATCHED);
        summary.add(ReconciliationResult.MISSING_INTERNAL);
        assertThat(summary.totalInternal()).isEqualTo(2);
        assertThat(summary.totalExternal()).isEqualTo(3);
        assertThat(summary.isFullyReconciled()).isFalse();
    }

    @Test
    void requiresAtLeastOneSide() {
        assertThatThrownBy(() -> exact.classify(null, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
