package com.smartops.api.query;

import com.smartops.api.error.ResourceNotFoundException;
import com.smartops.application.port.out.AccountRepository;
import com.smartops.application.query.OperationsQueries;
import com.smartops.application.query.OperationsQueries.AuditView;
import com.smartops.application.query.OperationsQueries.DashboardSummary;
import com.smartops.application.query.PageResult;
import com.smartops.application.query.PageResult.PageRequest;
import com.smartops.domain.account.BankAccount;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/** Consultas transversales: sesión, tablero, cuentas y auditoría. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Consultas", description = "Sesión, tablero, cuentas y auditoría")
public class QueryController {

    private final OperationsQueries queries;
    private final AccountRepository accounts;

    public QueryController(OperationsQueries queries, AccountRepository accounts) {
        this.queries = queries;
        this.accounts = accounts;
    }

    public record SessionResponse(String username, List<String> roles) { }

    public record AccountResponse(String accountNumber, String holderName, String currency, BigDecimal balance,
                                  String status) {
        static AccountResponse from(BankAccount a) {
            return new AccountResponse(a.getAccountNumber(), a.getHolderName(), a.getCurrency(), a.getBalance(),
                    a.getStatus().name());
        }
    }

    @GetMapping("/session")
    @Operation(summary = "Usuario autenticado y sus roles")
    public SessionResponse session(Authentication auth) {
        return new SessionResponse(auth.getName(), auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).map(r -> r.replace("ROLE_", "")).sorted().toList());
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Indicadores operativos")
    public DashboardSummary dashboard() {
        return queries.dashboard();
    }

    @GetMapping("/accounts")
    @Operation(summary = "Cuentas simuladas")
    @Transactional(readOnly = true)
    public List<AccountResponse> accounts() {
        return accounts.findAll().stream().map(AccountResponse::from).toList();
    }

    @GetMapping("/accounts/{number}")
    @Operation(summary = "Consultar una cuenta")
    @Transactional(readOnly = true)
    public AccountResponse account(@PathVariable String number) {
        return accounts.findByNumber(number).map(AccountResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("cuenta", number));
    }

    @GetMapping("/audit")
    @Operation(summary = "Rastro de auditoría (rol AUDITOR)")
    public PageResult<AuditView> audit(@RequestParam(required = false) String entityType,
                                       @RequestParam(required = false) String entityId,
                                       @RequestParam(required = false) String actor,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "50") int size) {
        return queries.searchAudit(entityType, entityId, actor, new PageRequest(page, size));
    }
}
