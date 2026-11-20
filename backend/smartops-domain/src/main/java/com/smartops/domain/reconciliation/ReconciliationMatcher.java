package com.smartops.domain.reconciliation;

import java.math.BigDecimal;

/**
 * Regla de comparación entre un movimiento interno y uno externo con la misma referencia.
 * Cualquiera de los dos lados puede ser {@code null} (no existe en ese lado).
 */
public class ReconciliationMatcher {

    public record Entry(BigDecimal amount, String currency) { }

    private final BigDecimal tolerance;

    public ReconciliationMatcher(BigDecimal tolerance) {
        if (tolerance == null || tolerance.signum() < 0) {
            throw new IllegalArgumentException("La tolerancia debe ser >= 0");
        }
        this.tolerance = tolerance;
    }

    public ReconciliationResult classify(Entry internal, Entry external) {
        if (internal == null && external == null) {
            throw new IllegalArgumentException("Debe existir al menos un lado para conciliar");
        }
        if (internal == null) {
            return ReconciliationResult.MISSING_INTERNAL;
        }
        if (external == null) {
            return ReconciliationResult.MISSING_EXTERNAL;
        }
        boolean sameCurrency = internal.currency().equalsIgnoreCase(external.currency());
        boolean amountWithinTolerance = internal.amount().subtract(external.amount()).abs().compareTo(tolerance) <= 0;
        return sameCurrency && amountWithinTolerance
                ? ReconciliationResult.MATCHED
                : ReconciliationResult.AMOUNT_MISMATCH;
    }

    /** Diferencia interno - externo; nula si falta algún lado. */
    public static BigDecimal difference(Entry internal, Entry external) {
        if (internal == null || external == null) {
            return null;
        }
        return internal.amount().subtract(external.amount());
    }
}
