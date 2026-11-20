package com.smartops.domain.reconciliation;

public enum ReconciliationResult {
    /** Existe en ambos lados con el mismo monto y moneda. */
    MATCHED,
    /** Existe en ambos lados pero el monto o la moneda difieren. */
    AMOUNT_MISMATCH,
    /** Procesada internamente pero no aparece en el extracto externo. */
    MISSING_EXTERNAL,
    /** Aparece en el extracto externo pero no fue procesada internamente. */
    MISSING_INTERNAL
}
