package com.smartops.domain.transaction;

import java.util.EnumSet;
import java.util.Set;

/**
 * Ciclo de vida de una operación.
 *
 * <pre>
 * RECEIVED ─► VALIDATED ─► PROCESSING ─► PROCESSED ─► RECONCILED
 *     │            │            │
 *     └────────────┴────────────┴──────► REJECTED
 * </pre>
 */
public enum TransactionStatus {
    RECEIVED,
    VALIDATED,
    PROCESSING,
    PROCESSED,
    REJECTED,
    RECONCILED;

    public Set<TransactionStatus> allowedTransitions() {
        return switch (this) {
            case RECEIVED -> EnumSet.of(VALIDATED, REJECTED);
            case VALIDATED -> EnumSet.of(PROCESSING, REJECTED);
            case PROCESSING -> EnumSet.of(PROCESSED, REJECTED);
            case PROCESSED -> EnumSet.of(RECONCILED);
            case REJECTED, RECONCILED -> EnumSet.noneOf(TransactionStatus.class);
        };
    }

    public boolean canTransitionTo(TransactionStatus target) {
        return allowedTransitions().contains(target);
    }

    public boolean isFinal() {
        return allowedTransitions().isEmpty();
    }
}
