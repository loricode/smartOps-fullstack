package com.smartops.api;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de extremo a extremo contra PostgreSQL real (Testcontainers):
 * migraciones Flyway → API idempotente → job de carga → job de conciliación → auditoría.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SmartOpsEndToEndIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void inbox(DynamicPropertyRegistry registry) throws Exception {
        String dir = Files.createTempDirectory("smartops-inbox").toString();
        registry.add("smartops.storage.inbox", () -> dir);
    }

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Bogota"));
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() { };

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private JdbcTemplate jdbc;

    private TestRestTemplate operator() {
        return rest.withBasicAuth("operator", "operator123");
    }

    // ------------------------------------------------------------------ API idempotente

    @Test
    @Order(1)
    void registersTransferIdempotently() {
        String body = """
                {"type":"TRANSFER","sourceAccount":"ACC0000001","targetAccount":"ACC0000002",
                 "amount":250000.00,"currency":"COP"}
                """;

        ResponseEntity<Map<String, Object>> first = postJson("/api/v1/transactions", body, "API-E2E-0001");
        ResponseEntity<Map<String, Object>> replay = postJson("/api/v1/transactions", body, "API-E2E-0001");
        ResponseEntity<Map<String, Object>> conflict = postJson("/api/v1/transactions",
                body.replace("250000.00", "1.00"), "API-E2E-0001");

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getBody()).containsEntry("status", "PROCESSED");
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
        assertThat(replay.getBody()).containsEntry("id", first.getBody().get("id"));
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // El débito se aplicó exactamente una vez
        assertThat(balance("ACC0000001")).isEqualByComparingTo("49750000.00");
        assertThat(balance("ACC0000002")).isEqualByComparingTo("50250000.00");
    }

    @Test
    @Order(2)
    void businessRejectionIsRecordedNotLost() {
        String body = """
                {"type":"PAYMENT","sourceAccount":"ACC0000200","amount":10.00,"currency":"COP"}
                """;
        ResponseEntity<Map<String, Object>> response = postJson("/api/v1/transactions", body, "API-E2E-0002");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsEntry("status", "REJECTED");
        assertThat((String) response.getBody().get("rejectionReason")).startsWith("ACCOUNT_BLOCKED");
    }

    // ------------------------------------------------------------------ carga masiva

    @Test
    @Order(3)
    void importsFileWithValidAndInvalidRecords() {
        StringBuilder csv = new StringBuilder("reference,type,source_account,target_account,amount,currency,value_date,description\n");
        for (int i = 1; i <= 1000; i++) {
            csv.append("BATCH-%05d,TRANSFER,ACC%07d,ACC%07d,%d.00,COP,%s,Lote E2E%n"
                    .formatted(i, (i % 150) + 1, (i % 150) + 2, 1000 + i, TODAY));
        }
        csv.append("BATCH-00001,PAYMENT,ACC0000001,,5.00,COP,").append(TODAY).append(",duplicada\n");
        csv.append("BATCH-X0001,TRANSFER,ACC0000001,ACC9999999,5.00,COP,").append(TODAY).append(",cuenta inexistente\n");
        csv.append("BATCH-X0002,TRANSFER,ACC0000001,ACC0000003,abc,COP,").append(TODAY).append(",monto inválido\n");
        csv.append("BATCH-X0003,PAYMENT,ACC0000005,,999999999.00,COP,").append(TODAY).append(",sin fondos\n");
        csv.append("linea,rota\n");

        ResponseEntity<Map<String, Object>> submitted = upload("/api/v1/batch/imports", "operaciones.csv", csv.toString());
        assertThat(submitted.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        long executionId = ((Number) submitted.getBody().get("executionId")).longValue();

        Map<String, Object> execution = awaitStatus("/api/v1/batch/executions/" + executionId);

        assertThat(execution).containsEntry("status", "COMPLETED");
        assertThat(execution).containsEntry("readCount", 1005);
        assertThat(execution).containsEntry("processedCount", 1000);
        assertThat(execution).containsEntry("rejectedCount", 5); // 4 en validación + 1 por fondos
        assertThat(execution).containsEntry("acceptedCount", 1000);

        ResponseEntity<List<Map<String, Object>>> summary = operator().exchange(
                "/api/v1/batch/executions/" + executionId + "/rejections/summary", HttpMethod.GET, null,
                new ParameterizedTypeReference<>() { });
        assertThat(summary.getBody()).extracting(m -> m.get("reasonCode"))
                .contains("DUPLICATE_REFERENCE", "ACCOUNT_NOT_FOUND", "INVALID_AMOUNT", "MALFORMED_RECORD",
                        "INSUFFICIENT_FUNDS");

        // El mismo archivo no se procesa dos veces
        ResponseEntity<Map<String, Object>> again = upload("/api/v1/batch/imports", "copia.csv", csv.toString());
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(again.getBody()).containsEntry("code", "DUPLICATE_FILE");
    }

    // ------------------------------------------------------------------ conciliación

    @Test
    @Order(4)
    void reconcilesAgainstExternalStatement() {
        StringBuilder csv = new StringBuilder("reference,amount,currency,value_date\n");
        for (int i = 1; i <= 1000; i++) {
            if (i <= 10) {
                continue;                                           // 10 faltan en el extracto
            }
            String amount = i <= 15 ? "1.00" : (1000 + i) + ".00";  // 5 con monto distinto
            csv.append("BATCH-%05d,%s,COP,%s%n".formatted(i, amount, TODAY));
        }
        csv.append("EXT-ONLY-1,500.00,COP,").append(TODAY).append('\n'); // 2 solo en el extracto
        csv.append("EXT-ONLY-2,700.00,COP,").append(TODAY).append('\n');
        csv.append("EXT-ONLY-2,700.00,COP,").append(TODAY).append('\n'); // duplicada → omitida
        csv.append("BAD,xyz,COP,").append(TODAY).append('\n');            // inválida → omitida

        ResponseEntity<Map<String, Object>> submitted = upload("/api/v1/reconciliations", "extracto.csv", csv.toString());
        assertThat(submitted.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        long id = ((Number) submitted.getBody().get("reconciliationId")).longValue();

        Map<String, Object> rec = awaitStatus("/api/v1/reconciliations/" + id);

        assertThat(rec).containsEntry("status", "COMPLETED");
        assertThat(rec).containsEntry("matchedCount", 985);
        assertThat(rec).containsEntry("amountMismatchCount", 5);
        assertThat(rec).containsEntry("missingInternalCount", 2);
        // 10 del lote + la transferencia del API (procesada hoy) no figuran en el extracto
        assertThat(rec).containsEntry("missingExternalCount", 11);

        Integer reconciled = jdbc.queryForObject(
                "SELECT count(*) FROM smartops.financial_transaction WHERE status = 'RECONCILED'", Integer.class);
        assertThat(reconciled).isEqualTo(985);
    }

    // ------------------------------------------------------------------ auditoría y seguridad

    @Test
    @Order(5)
    void auditTrailIsVisibleToAuditorOnly() {
        ResponseEntity<Map<String, Object>> asAuditor = rest.withBasicAuth("auditor", "auditor123").exchange(
                "/api/v1/audit?entityType=BATCH_EXECUTION", HttpMethod.GET, null, MAP);
        ResponseEntity<Map<String, Object>> asOperator = operator().exchange(
                "/api/v1/audit", HttpMethod.GET, null, MAP);

        assertThat(asAuditor.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) asAuditor.getBody().get("totalElements")).intValue()).isGreaterThanOrEqualTo(1);
        assertThat(asOperator.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(6)
    void auditLogIsAppendOnly() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("DELETE FROM smartops.audit_log"))
                .hasMessageContaining("solo insercion");
    }

    // ------------------------------------------------------------------ utilidades

    private ResponseEntity<Map<String, Object>> postJson(String url, String body, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);
        return operator().exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), MAP);
    }

    private ResponseEntity<Map<String, Object>> upload(String url, String fileName, String content) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return operator().exchange(url, HttpMethod.POST, new HttpEntity<>(form, headers), MAP);
    }

    private Map<String, Object> awaitStatus(String url) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(60));
        while (Instant.now().isBefore(deadline)) {
            Map<String, Object> body = operator().exchange(url, HttpMethod.GET, null, MAP).getBody();
            if (body != null && !"STARTED".equals(body.get("status"))) {
                return body;
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("Timeout esperando " + url);
    }

    private java.math.BigDecimal balance(String account) {
        return jdbc.queryForObject("SELECT balance FROM smartops.bank_account WHERE account_number = ?",
                java.math.BigDecimal.class, account);
    }
}
