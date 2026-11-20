package com.smartops.application.transaction;

import com.smartops.application.port.out.TransactionRepository;
import com.smartops.application.port.out.TransactionRepository.DuplicateReferenceException;
import com.smartops.domain.transaction.Channel;
import com.smartops.domain.transaction.Transaction;
import com.smartops.domain.transaction.TransactionDraft;
import com.smartops.domain.transaction.TransactionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionRegistrationServiceTest {

    @Mock
    private TransactionRepository transactions;
    @Mock
    private TransactionRegistrar registrar;
    @InjectMocks
    private TransactionRegistrationService service;

    private static TransactionDraft draft(String amount) {
        return new TransactionDraft("IDEM-0001", TransactionType.TRANSFER, "ACC0000001", "ACC0000002",
                new BigDecimal(amount), "COP", LocalDate.of(2026, 11, 19), null);
    }

    private static Transaction existing(TransactionDraft draft) {
        return Transaction.receive(draft, Channel.API, null, "operator", Instant.now());
    }

    @Test
    void registersNewOperation() {
        TransactionDraft draft = draft("100.00");
        Transaction created = existing(draft);
        when(transactions.findByReference("IDEM-0001")).thenReturn(Optional.empty());
        when(registrar.registerNew(draft, "operator")).thenReturn(created);

        RegistrationResult result = service.register(draft, "operator");

        assertThat(result.replayed()).isFalse();
        assertThat(result.transaction()).isSameAs(created);
    }

    @Test
    void replaysWhenSameReferenceAndSamePayload() {
        TransactionDraft draft = draft("100.00");
        Transaction original = existing(draft);
        when(transactions.findByReference("IDEM-0001")).thenReturn(Optional.of(original));

        RegistrationResult result = service.register(draft("100"), "operator");

        assertThat(result.replayed()).isTrue();
        assertThat(result.transaction()).isSameAs(original);
        verify(registrar, never()).registerNew(any(), anyString());
    }

    @Test
    void conflictWhenSameReferenceWithDifferentPayload() {
        when(transactions.findByReference("IDEM-0001")).thenReturn(Optional.of(existing(draft("100.00"))));

        assertThatThrownBy(() -> service.register(draft("999.00"), "operator"))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("IDEM-0001");
        verify(registrar, never()).registerNew(any(), anyString());
    }

    @Test
    void concurrentDuplicateIsResolvedAsReplay() {
        TransactionDraft draft = draft("100.00");
        Transaction winner = existing(draft);
        when(transactions.findByReference("IDEM-0001"))
                .thenReturn(Optional.empty())       // primera lectura: aún no existe
                .thenReturn(Optional.of(winner));   // tras perder la carrera: ya existe
        when(registrar.registerNew(draft, "operator"))
                .thenThrow(new DuplicateReferenceException("IDEM-0001", null));

        RegistrationResult result = service.register(draft, "operator");

        assertThat(result.replayed()).isTrue();
        assertThat(result.transaction()).isSameAs(winner);
    }
}
