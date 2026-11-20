package com.smartops.batch.reconciliation;

public class DuplicateStatementLineException extends RuntimeException {

    public DuplicateStatementLineException(String reference) {
        super("Referencia repetida en el extracto: " + reference);
    }
}
