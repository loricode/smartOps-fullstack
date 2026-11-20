package com.smartops.batch.importjob;

import com.smartops.domain.transaction.Transaction;

/**
 * Resultado de evaluar una línea. Un rechazo de negocio es un dato que se persiste,
 * no una excepción: así el lote nunca se detiene por registros inválidos.
 */
public sealed interface ImportOutcome {

    int lineNumber();

    record Accepted(int lineNumber, Transaction transaction) implements ImportOutcome { }

    record Rejected(int lineNumber, String reference, String reasonCode, String reasonDetail, String rawLine)
            implements ImportOutcome { }
}
