package com.smartops.application.port.out;

import com.smartops.domain.account.AccountDirectory;
import com.smartops.domain.account.BankAccount;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface AccountRepository extends AccountDirectory {

    /**
     * Bloquea (SELECT ... FOR UPDATE) las cuentas indicadas, siempre en orden
     * ascendente de número de cuenta para evitar interbloqueos entre operaciones concurrentes.
     */
    Map<String, BankAccount> lockForPosting(Collection<String> accountNumbers);

    void saveBalance(BankAccount account);

    List<BankAccount> findAll();
}
