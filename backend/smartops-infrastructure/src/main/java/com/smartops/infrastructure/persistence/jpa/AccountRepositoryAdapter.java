package com.smartops.infrastructure.persistence.jpa;

import com.smartops.application.port.out.AccountRepository;
import com.smartops.domain.account.AccountStatus;
import com.smartops.domain.account.BankAccount;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

@Repository
class AccountRepositoryAdapter implements AccountRepository {

    private final BankAccountJpaRepository jpa;

    AccountRepositoryAdapter(BankAccountJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<BankAccount> findByNumber(String accountNumber) {
        return jpa.findByAccountNumber(accountNumber).map(AccountRepositoryAdapter::toDomain);
    }

    @Override
    public Map<String, BankAccount> lockForPosting(Collection<String> accountNumbers) {
        Map<String, BankAccount> result = new LinkedHashMap<>();
        if (accountNumbers.isEmpty()) {
            return result;
        }
        jpa.lockByAccountNumbers(new TreeSet<>(accountNumbers))
                .forEach(e -> result.put(e.getAccountNumber(), toDomain(e)));
        return result;
    }

    @Override
    public void saveBalance(BankAccount account) {
        // La entidad ya está en el contexto de persistencia (fue bloqueada); el dirty-checking hace el UPDATE.
        BankAccountEntity entity = jpa.findByAccountNumber(account.getAccountNumber())
                .orElseThrow(() -> new IllegalStateException("Cuenta inexistente " + account.getAccountNumber()));
        entity.setBalance(account.getBalance());
    }

    @Override
    public List<BankAccount> findAll() {
        return jpa.findAllByOrderByAccountNumberAsc().stream().map(AccountRepositoryAdapter::toDomain).toList();
    }

    private static BankAccount toDomain(BankAccountEntity e) {
        return new BankAccount(e.getId(), e.getAccountNumber(), e.getHolderName(), e.getCurrency(),
                e.getBalance(), AccountStatus.valueOf(e.getStatus()), e.getVersion());
    }
}
