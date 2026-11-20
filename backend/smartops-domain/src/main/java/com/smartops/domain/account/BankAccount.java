package com.smartops.domain.account;

import com.smartops.domain.shared.DomainException;
import com.smartops.domain.shared.ValidationCode;

import java.math.BigDecimal;
import java.util.Objects;

/** Cuenta bancaria simulada. El saldo solo cambia mediante {@link #debit} y {@link #credit}. */
public class BankAccount {

    private final Long id;
    private final String accountNumber;
    private final String holderName;
    private final String currency;
    private final AccountStatus status;
    private final Long version;
    private BigDecimal balance;

    public BankAccount(Long id, String accountNumber, String holderName, String currency,
                       BigDecimal balance, AccountStatus status, Long version) {
        this.id = id;
        this.accountNumber = Objects.requireNonNull(accountNumber, "accountNumber");
        this.holderName = holderName;
        this.currency = Objects.requireNonNull(currency, "currency");
        this.balance = Objects.requireNonNull(balance, "balance");
        this.status = Objects.requireNonNull(status, "status");
        this.version = version;
    }

    public void debit(BigDecimal amount) {
        requirePositive(amount);
        requireActive();
        if (balance.compareTo(amount) < 0) {
            throw new DomainException(ValidationCode.INSUFFICIENT_FUNDS.name(),
                    "Saldo insuficiente en la cuenta " + accountNumber);
        }
        balance = balance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        requirePositive(amount);
        requireActive();
        balance = balance.add(amount);
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    private void requireActive() {
        if (!isActive()) {
            throw new DomainException(ValidationCode.ACCOUNT_BLOCKED.name(),
                    "La cuenta " + accountNumber + " está bloqueada");
        }
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("El monto debe ser positivo");
        }
    }

    public Long getId() { return id; }
    public String getAccountNumber() { return accountNumber; }
    public String getHolderName() { return holderName; }
    public String getCurrency() { return currency; }
    public BigDecimal getBalance() { return balance; }
    public AccountStatus getStatus() { return status; }
    public Long getVersion() { return version; }
}
