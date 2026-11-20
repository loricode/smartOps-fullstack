package com.smartops.infrastructure.persistence.jpa;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BankAccountJpaRepository extends JpaRepository<BankAccountEntity, Long> {

    Optional<BankAccountEntity> findByAccountNumber(String accountNumber);

    /** SELECT ... ORDER BY account_number FOR UPDATE: orden de bloqueo determinista. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select a from BankAccountEntity a where a.accountNumber in :numbers order by a.accountNumber")
    List<BankAccountEntity> lockByAccountNumbers(@Param("numbers") Collection<String> numbers);

    List<BankAccountEntity> findAllByOrderByAccountNumberAsc();
}
