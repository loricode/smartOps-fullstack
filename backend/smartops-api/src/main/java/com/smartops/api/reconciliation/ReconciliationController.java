package com.smartops.api.reconciliation;

import com.smartops.api.error.ResourceNotFoundException;
import com.smartops.application.intake.FileIntakeService;
import com.smartops.application.intake.FileIntakeService.IntakeResult;
import com.smartops.application.query.OperationsQueries;
import com.smartops.application.query.OperationsQueries.ReconciliationDetailView;
import com.smartops.application.query.OperationsQueries.ReconciliationView;
import com.smartops.application.query.PageResult;
import com.smartops.application.query.PageResult.PageRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.Principal;

@RestController
@RequestMapping("/api/v1/reconciliations")
@Tag(name = "Conciliación", description = "Comparación contra extractos externos")
public class ReconciliationController {

    private final FileIntakeService intake;
    private final OperationsQueries queries;

    public ReconciliationController(FileIntakeService intake, OperationsQueries queries) {
        this.intake = intake;
        this.queries = queries;
    }

    public record SubmissionResponse(long statementFileId, long reconciliationId, URI statusUrl) { }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Recibir un extracto externo y conciliar",
            description = "CSV con cabecera reference,amount,currency,value_date. Responde 202 y concilia en segundo plano.")
    public ResponseEntity<SubmissionResponse> submit(@RequestPart("file") MultipartFile file, Principal principal)
            throws IOException {
        if (file.isEmpty() || file.getOriginalFilename() == null
                || !file.getOriginalFilename().toLowerCase().endsWith(".csv")) {
            throw new IllegalArgumentException("Se requiere un archivo .csv no vacío");
        }
        IntakeResult result;
        try (InputStream in = file.getInputStream()) {
            result = intake.submitStatementFile(file.getOriginalFilename(), in, principal.getName());
        }
        URI status = URI.create("/api/v1/reconciliations/" + result.executionId());
        return ResponseEntity.accepted().location(status)
                .body(new SubmissionResponse(result.fileId(), result.executionId(), status));
    }

    @GetMapping
    @Operation(summary = "Listar conciliaciones")
    public PageResult<ReconciliationView> list(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return queries.listReconciliations(new PageRequest(page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Resumen de una conciliación")
    public ReconciliationView get(@PathVariable long id) {
        return queries.findReconciliation(id).orElseThrow(() -> new ResourceNotFoundException("conciliación", id));
    }

    @GetMapping("/{id}/details")
    @Operation(summary = "Detalle por referencia; filtrable por resultado (MATCHED, AMOUNT_MISMATCH, MISSING_EXTERNAL, MISSING_INTERNAL)")
    public PageResult<ReconciliationDetailView> details(@PathVariable long id,
                                                        @RequestParam(required = false) String result,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "50") int size) {
        get(id);
        return queries.listReconciliationDetails(id, result, new PageRequest(page, size));
    }
}
