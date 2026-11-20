package com.smartops.domain.reconciliation;

import java.util.EnumMap;
import java.util.Map;

/** Acumulador de resultados de una conciliación. */
public class ReconciliationSummary {

    private final Map<ReconciliationResult, Integer> counts = new EnumMap<>(ReconciliationResult.class);

    public void add(ReconciliationResult result) {
        counts.merge(result, 1, Integer::sum);
    }

    public int count(ReconciliationResult result) {
        return counts.getOrDefault(result, 0);
    }

    /** Registros con contraparte interna (todo excepto MISSING_INTERNAL). */
    public int totalInternal() {
        return count(ReconciliationResult.MATCHED) + count(ReconciliationResult.AMOUNT_MISMATCH)
                + count(ReconciliationResult.MISSING_EXTERNAL);
    }

    /** Registros con contraparte externa (todo excepto MISSING_EXTERNAL). */
    public int totalExternal() {
        return count(ReconciliationResult.MATCHED) + count(ReconciliationResult.AMOUNT_MISMATCH)
                + count(ReconciliationResult.MISSING_INTERNAL);
    }

    public boolean isFullyReconciled() {
        return count(ReconciliationResult.MATCHED) == totalInternal()
                && count(ReconciliationResult.MATCHED) == totalExternal();
    }
}
