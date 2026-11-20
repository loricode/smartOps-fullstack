package com.smartops.application.config;

import com.smartops.domain.reconciliation.ReconciliationMatcher;
import com.smartops.domain.transaction.LedgerPostingService;
import com.smartops.domain.transaction.TransactionPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;

/** Registra los servicios de dominio (POJOs) como beans; el dominio no conoce Spring. */
@Configuration
public class DomainConfig {

    @Bean
    public Clock clock(@Value("${smartops.zone:America/Bogota}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }

    @Bean
    public TransactionPolicy transactionPolicy(
            @Value("${smartops.limits.max-amount-per-operation:1000000000}") BigDecimal maxAmount, Clock clock) {
        return new TransactionPolicy(maxAmount, clock);
    }

    @Bean
    public LedgerPostingService ledgerPostingService() {
        return new LedgerPostingService();
    }

    @Bean
    public ReconciliationMatcher reconciliationMatcher(
            @Value("${smartops.reconciliation.tolerance:0.00}") BigDecimal tolerance) {
        return new ReconciliationMatcher(tolerance);
    }
}
