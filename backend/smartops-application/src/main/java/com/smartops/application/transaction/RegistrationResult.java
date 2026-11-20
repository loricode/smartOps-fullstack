package com.smartops.application.transaction;

import com.smartops.domain.transaction.Transaction;

/**
 * @param replayed {@code true} si la petición era un reintento de una operación ya registrada:
 *                 se devuelve el resultado original sin volver a procesarla.
 */
public record RegistrationResult(Transaction transaction, boolean replayed) { }
