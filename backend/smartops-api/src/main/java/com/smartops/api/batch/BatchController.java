package com.smartops.api.batch;

import com.smartops.api.error.ResourceNotFoundException;
import com.smartops.application.intake.FileIntakeService;
import com.smartops.application.intake.FileIntakeService.IntakeResult;
import com.smartops.application.query.OperationsQueries;
import com.smartops.application.query.OperationsQueries.BatchExecutionView;
import com.smartops.application.query.OperationsQueries.RejectedRecordView;
import com.smartops.application.query.OperationsQueries.RejectionCount;
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
import java.util.List;

@RestController
@RequestMapping("/api/v1/batch")
@Tag(name = "Procesamiento masivo", description = "Carga de archivos de operaciones (Spring Batch)")
public class BatchController {

    private final FileIntakeService intake;
    private final OperationsQueries queries;

    public BatchController(FileIntakeService intake, OperationsQueries queries) {
        this.intake = intake;
        this.queries = queries;
    }

    public record SubmissionResponse(long fileId, long executionId, URI statusUrl) { }

    @PostMapping(path = "/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Recibir un archivo CSV de operaciones",
            description = "Responde 202 y procesa en segundo plano. Un archivo con contenido idéntico a uno ya recibido se rechaza (409).")
    public ResponseEntity<SubmissionResponse> submit(@RequestPart("file") MultipartFile file, Principal principal)
            throws IOException {
        requireCsv(file);
        IntakeResult result;
        try (InputStream in = file.getInputStream()) {
            result = intake.submitOperationsFile(file.getOriginalFilename(), in, principal.getName());
        }
        URI status = URI.create("/api/v1/batch/executions/" + result.executionId());
        return ResponseEntity.accepted().location(status)
                .body(new SubmissionResponse(result.fileId(), result.executionId(), status));
    }

    @GetMapping("/executions")
    @Operation(summary = "Listar ejecuciones de lote")
    public PageResult<BatchExecutionView> list(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return queries.listBatchExecutions(new PageRequest(page, size));
    }

    @GetMapping("/executions/{id}")
    @Operation(summary = "Estado y métricas de una ejecución")
    public BatchExecutionView get(@PathVariable long id) {
        return find(id);
    }

    @GetMapping("/executions/{id}/rejections")
    @Operation(summary = "Registros rechazados en la validación del archivo")
    public PageResult<RejectedRecordView> rejections(@PathVariable long id,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "50") int size) {
        return queries.listRejectedRecords(find(id).batchFileId(), new PageRequest(page, size));
    }

    @GetMapping("/executions/{id}/rejections/summary")
    @Operation(summary = "Rechazos agrupados por motivo (validación + contabilización)")
    public List<RejectionCount> rejectionSummary(@PathVariable long id) {
        return queries.rejectionsByReason(find(id).batchFileId());
    }

    private BatchExecutionView find(long id) {
        return queries.findBatchExecution(id).orElseThrow(() -> new ResourceNotFoundException("ejecución", id));
    }

    static void requireCsv(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("El archivo está vacío");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".csv")) {
            throw new IllegalArgumentException("Solo se aceptan archivos .csv");
        }
    }
}
