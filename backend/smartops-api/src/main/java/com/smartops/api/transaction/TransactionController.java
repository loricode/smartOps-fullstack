package com.smartops.api.transaction;

import com.smartops.application.query.OperationsQueries;
import com.smartops.application.query.OperationsQueries.TransactionFilter;
import com.smartops.application.query.OperationsQueries.TransactionView;
import com.smartops.application.query.PageResult;
import com.smartops.application.query.PageResult.PageRequest;
import com.smartops.application.transaction.RegistrationResult;
import com.smartops.application.transaction.TransactionRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.security.Principal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Operaciones", description = "Registro idempotente y consulta de operaciones")
public class TransactionController {

    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private final TransactionRegistrationService registration;
    private final OperationsQueries queries;
    private final Clock clock;

    public TransactionController(TransactionRegistrationService registration, OperationsQueries queries,
                                 Clock clock) {
        this.registration = registration;
        this.queries = queries;
        this.clock = clock;
    }

    @PostMapping
    @Operation(summary = "Registrar una operación",
            description = """
                    La cabecera Idempotency-Key es la referencia única de la operación. Reenviar la misma \
                    solicitud devuelve el resultado original (200 + Idempotent-Replayed: true) sin volver a \
                    procesarla; reutilizar la clave con otro contenido devuelve 409. Una operación que no \
                    supera las reglas de negocio se registra igualmente con estado REJECTED (201).""")
    @ApiResponse(responseCode = "201", description = "Operación registrada (PROCESSED o REJECTED)")
    @ApiResponse(responseCode = "200", description = "Reintento: se devuelve la operación original")
    @ApiResponse(responseCode = "409", description = "Clave de idempotencia reutilizada con otro contenido")
    public ResponseEntity<TransactionResponse> register(
            @Parameter(description = "Referencia única (4-64 caracteres alfanuméricos, '-' o '_')")
            @RequestHeader(IDEMPOTENCY_KEY)
            @Pattern(regexp = "^[A-Za-z0-9_-]{4,64}$", message = "Idempotency-Key inválida") String idempotencyKey,
            @Valid @RequestBody CreateTransactionRequest request,
            Principal principal) {
        RegistrationResult result = registration.register(
                request.toDraft(idempotencyKey, LocalDate.now(clock)), principal.getName());
        TransactionResponse body = TransactionResponse.from(result.transaction());
        if (result.replayed()) {
            return ResponseEntity.ok().header(REPLAYED_HEADER, "true").body(body);
        }
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}").buildAndExpand(body.id()).toUri())
                .body(body);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar una operación")
    public TransactionResponse get(@PathVariable UUID id) {
        return TransactionResponse.from(registration.get(id));
    }

    @GetMapping
    @Operation(summary = "Buscar operaciones (paginado)")
    public PageResult<TransactionView> search(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String reference,
            @RequestParam(required = false) Long batchFileId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return queries.searchTransactions(new TransactionFilter(status, type, channel, reference, batchFileId,
                from, to), new PageRequest(page, size));
    }
}
