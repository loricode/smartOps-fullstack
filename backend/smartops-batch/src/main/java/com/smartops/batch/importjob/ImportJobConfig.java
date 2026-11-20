package com.smartops.batch.importjob;

import com.smartops.application.port.out.AccountRepository;
import com.smartops.application.port.out.AuditTrail;
import com.smartops.application.port.out.BatchJobLauncher;
import com.smartops.application.port.out.TransactionRepository;
import com.smartops.application.transaction.TransactionPostingService;
import com.smartops.batch.support.HeaderValidator;
import com.smartops.domain.transaction.TransactionPolicy;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/**
 * Job de carga masiva de operaciones.
 *
 * <pre>
 * [validateAndLoadStep]  lee CSV → valida → inserta VALIDATED / registra rechazos   (chunk 500)
 *          │
 * [postingStep]          lee VALIDATED del archivo → contabiliza saldos              (chunk 200)
 * </pre>
 */
@Configuration
public class ImportJobConfig {

    @Value("${smartops.batch.import.chunk-size:500}")
    private int loadChunkSize;

    @Value("${smartops.batch.posting.chunk-size:200}")
    private int postingChunkSize;

    @Value("${smartops.batch.retry-limit:3}")
    private int retryLimit;

    @Bean
    public Job importOperationsJob(JobRepository jobRepository, Step validateAndLoadStep, Step postingStep,
                                   JdbcTemplate jdbc, AuditTrail audit) {
        return new JobBuilder(BatchJobLauncher.IMPORT_JOB, jobRepository)
                .listener(new ImportJobListener(jdbc, audit))
                .start(validateAndLoadStep)
                .next(postingStep)
                .build();
    }

    @Bean
    public Step validateAndLoadStep(JobRepository jobRepository, PlatformTransactionManager txManager,
                                    FlatFileItemReader<OperationLine> operationFileReader,
                                    OperationValidationProcessor operationValidationProcessor,
                                    ImportOutcomeWriter importOutcomeWriter,
                                    ImportSkipListener importSkipListener) {
        return new StepBuilder("validateAndLoadStep", jobRepository)
                .<OperationLine, ImportOutcome>chunk(loadChunkSize, txManager)
                .reader(operationFileReader)
                .processor(operationValidationProcessor)
                .writer(importOutcomeWriter)
                .faultTolerant()
                // errores transitorios (bloqueos, timeouts) → reintento controlado
                .retry(TransientDataAccessException.class)
                .retryLimit(retryLimit)
                // carrera de idempotencia con el API → se omite el ítem y se registra
                .skip(DuplicateKeyException.class)
                .skipLimit(Integer.MAX_VALUE)
                // el procesador tiene estado (referencias vistas): en un reintento se reutiliza su
                // salida en lugar de volver a evaluarlo, o las líneas se marcarían como duplicadas
                .processorNonTransactional()
                .listener(importSkipListener)
                .build();
    }

    @Bean
    public Step postingStep(JobRepository jobRepository, PlatformTransactionManager txManager,
                            JdbcPagingItemReader<UUID> validatedTransactionReader, PostingWriter postingWriter) {
        return new StepBuilder("postingStep", jobRepository)
                .<UUID, UUID>chunk(postingChunkSize, txManager)
                .reader(validatedTransactionReader)
                .writer(postingWriter)
                .faultTolerant()
                .retry(TransientDataAccessException.class)
                .retryLimit(retryLimit)
                .build();
    }

    // ------------------------------------------------------------------ componentes de ámbito step

    @Bean
    @StepScope
    public FlatFileItemReader<OperationLine> operationFileReader(
            @Value("#{jobParameters['filePath']}") String filePath) {
        return new FlatFileItemReaderBuilder<OperationLine>()
                .name("operationFileReader")
                .resource(new FileSystemResource(filePath))
                .encoding(StandardCharsets.UTF_8.name())
                .linesToSkip(1)
                .skippedLinesCallback(new HeaderValidator(OperationLineParser.HEADER))
                .lineMapper((line, lineNumber) -> new OperationLine(lineNumber, line))
                .build();
    }

    @Bean
    @StepScope
    public OperationValidationProcessor operationValidationProcessor(
            TransactionPolicy policy, AccountRepository accounts, JdbcTemplate jdbc, Clock clock,
            @Value("#{jobParameters['batchFileId']}") Long batchFileId,
            @Value("#{jobParameters['actor']}") String actor) {
        return new OperationValidationProcessor(policy, accounts,
                reference -> Boolean.TRUE.equals(jdbc.queryForObject(
                        "SELECT EXISTS (SELECT 1 FROM smartops.financial_transaction WHERE reference = ?)",
                        Boolean.class, reference)),
                batchFileId, actor, clock);
    }

    @Bean
    @StepScope
    public ImportOutcomeWriter importOutcomeWriter(JdbcTemplate jdbc,
                                                   @Value("#{jobParameters['batchFileId']}") Long batchFileId) {
        return new ImportOutcomeWriter(jdbc, batchFileId);
    }

    @Bean
    @StepScope
    public ImportSkipListener importSkipListener(JdbcTemplate jdbc,
                                                 @Value("#{jobParameters['batchFileId']}") Long batchFileId) {
        return new ImportSkipListener(jdbc, batchFileId);
    }

    /**
     * Paginación por clave (WHERE id > :último) y no por OFFSET: como cada página cambia el
     * estado de sus filas a PROCESSED, un OFFSET saltaría registros.
     */
    @Bean
    @StepScope
    public JdbcPagingItemReader<UUID> validatedTransactionReader(
            DataSource dataSource, @Value("#{jobParameters['batchFileId']}") Long batchFileId) {
        return new JdbcPagingItemReaderBuilder<UUID>()
                .name("validatedTransactionReader")
                .dataSource(dataSource)
                .selectClause("SELECT id")
                .fromClause("FROM smartops.financial_transaction")
                .whereClause("WHERE batch_file_id = :batchFileId AND status = 'VALIDATED'")
                .parameterValues(Map.of("batchFileId", batchFileId))
                .sortKeys(Map.of("id", Order.ASCENDING))
                .pageSize(postingChunkSize)
                .rowMapper((rs, i) -> rs.getObject("id", UUID.class))
                .build();
    }

    @Bean
    @StepScope
    public PostingWriter postingWriter(TransactionRepository transactions, TransactionPostingService posting,
                                       @Value("#{jobParameters['actor']}") String actor) {
        return new PostingWriter(transactions, posting, actor);
    }
}
