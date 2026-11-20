package com.smartops.api.transaction;

import com.smartops.api.config.SecurityConfig;
import com.smartops.application.query.OperationsQueries;
import com.smartops.application.transaction.IdempotencyConflictException;
import com.smartops.application.transaction.RegistrationResult;
import com.smartops.application.transaction.TransactionRegistrationService;
import com.smartops.domain.transaction.Channel;
import com.smartops.domain.transaction.Transaction;
import com.smartops.domain.transaction.TransactionDraft;
import com.smartops.domain.transaction.TransactionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
@Import(SecurityConfig.class)
class TransactionControllerTest {

    private static final String BODY = """
            {"type":"TRANSFER","sourceAccount":"ACC0000001","targetAccount":"ACC0000002",
             "amount":1500.00,"currency":"COP","valueDate":"2026-11-19"}
            """;

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private TransactionRegistrationService registration;
    @MockitoBean
    private OperationsQueries queries;
    @MockitoBean
    private Clock clock;

    private static Transaction tx() {
        return Transaction.receive(new TransactionDraft("IDEM-0001", TransactionType.TRANSFER, "ACC0000001",
                        "ACC0000002", new BigDecimal("1500.00"), "COP", LocalDate.of(2026, 11, 19), null),
                Channel.API, null, "operator", Instant.parse("2026-11-19T15:00:00Z"));
    }

    private void stubClock() {
        when(clock.instant()).thenReturn(Instant.parse("2026-11-19T15:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void auditorCannotRegisterOperations() throws Exception {
        mvc.perform(post("/api/v1/transactions").with(httpBasic("auditor", "auditor123"))
                        .header("Idempotency-Key", "IDEM-0001")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void createsOperation() throws Exception {
        stubClock();
        when(registration.register(any(), eq("operator"))).thenReturn(new RegistrationResult(tx(), false));

        mvc.perform(post("/api/v1/transactions").with(httpBasic("operator", "operator123"))
                        .header("Idempotency-Key", "IDEM-0001")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(jsonPath("$.reference").value("IDEM-0001"))
                .andExpect(jsonPath("$.channel").value("API"));
    }

    @Test
    void replayReturns200WithHeader() throws Exception {
        stubClock();
        when(registration.register(any(), any())).thenReturn(new RegistrationResult(tx(), true));

        mvc.perform(post("/api/v1/transactions").with(httpBasic("operator", "operator123"))
                        .header("Idempotency-Key", "IDEM-0001")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"));
    }

    @Test
    void conflictingReuseOfKeyReturns409() throws Exception {
        stubClock();
        when(registration.register(any(), any())).thenThrow(new IdempotencyConflictException("IDEM-0001"));

        mvc.perform(post("/api/v1/transactions").with(httpBasic("operator", "operator123"))
                        .header("Idempotency-Key", "IDEM-0001")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void validatesRequestShapeAndHeader() throws Exception {
        mvc.perform(post("/api/v1/transactions").with(httpBasic("operator", "operator123"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/transactions").with(httpBasic("operator", "operator123"))
                        .header("Idempotency-Key", "IDEM-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"TRANSFER\",\"amount\":-5,\"currency\":\"pesos\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.length()").value(2));

        mvc.perform(post("/api/v1/transactions").with(httpBasic("operator", "operator123"))
                        .header("Idempotency-Key", "bad key!")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
