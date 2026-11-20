package com.smartops.application.transaction;

import com.smartops.application.port.out.AccountRepository;
import com.smartops.application.port.out.AuditTrail;
import com.smartops.application.port.out.TransactionRepository;
import com.smartops.domain.transaction.Transaction;
import com.smartops.domain.transaction.TransactionDraft;
import com.smartops.domain.transaction.TransactionPolicy;
import com.smartops.domain.transaction.TransactionStatus;
import com.smartops.domain.transaction.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionRegistrarTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-19T15:00:00Z"), ZoneOffset.UTC);

    @Mock
    private TransactionRepository transactions;
    @Mock
    private AccountRepository accounts;
    @Mock
    private TransactionPostingService posting;
    @Mock
    private AuditTrail audit;

    private TransactionRegistrar registrar;

    @BeforeEach
    void setUp() {
        TransactionPolicy policy = new TransactionPolicy(new BigDecimal("1000000"), CLOCK);
        registrar = new TransactionRegistrar(transactions, accounts, policy, posting, audit, CLOCK);
        when(transactions.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void invalidOperationIsPersistedAsRejectedAndNeverPosted() {
        when(accounts.findByNumber("ACC9999999")).thenReturn(Optional.empty());
        TransactionDraft draft = new TransactionDraft("REF-0001", TransactionType.PAYMENT, "ACC9999999", null,
                BigDecimal.TEN, "COP", LocalDate.of(2026, 11, 19), null);

        Transaction result = registrar.registerNew(draft, "operator");

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.REJECTED);
        assertThat(result.getRejectionReason()).startsWith("ACCOUNT_NOT_FOUND");
        verify(posting, never()).post(any(), any());
        verify(audit).record(argThat(e -> e.action().equals("REJECTED")));
    }

    @Test
    void validOperationIsValidatedThenPosted() {
        when(accounts.findByNumber(any())).thenReturn(Optional.of(
                new com.smartops.domain.account.BankAccount(1L, "ACC0000001", "Demo", "COP", BigDecimal.TEN,
                        com.smartops.domain.account.AccountStatus.ACTIVE, 0L)));
        TransactionDraft draft = new TransactionDraft("REF-0001", TransactionType.PAYMENT, "ACC0000001", null,
                BigDecimal.ONE, "COP", LocalDate.of(2026, 11, 19), null);

        registrar.registerNew(draft, "operator");

        verify(posting).post(argThat(tx -> tx.getStatus() == TransactionStatus.VALIDATED), eq("operator"));
    }
}
