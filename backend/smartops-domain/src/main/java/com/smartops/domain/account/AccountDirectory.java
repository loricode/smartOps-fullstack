package com.smartops.domain.account;

import java.util.Optional;

/** Consulta de cuentas requerida por las reglas de validación (sin bloqueo). */
@FunctionalInterface
public interface AccountDirectory {

    Optional<BankAccount> findByNumber(String accountNumber);
}
