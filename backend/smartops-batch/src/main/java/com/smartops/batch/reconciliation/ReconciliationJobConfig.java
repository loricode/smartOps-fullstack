package com.smartops.batch.reconciliation;

import com.smartops.application.port.out.AuditTrail;
import com.smartops.application.port.out.BatchJobLauncher;
import com.smartops.batch.support.HeaderValidator;
import com.smartops.domain.reconciliation.ReconciliationMatcher;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Job de conciliación.
 *
 * <pre>
 * [loadStatementStep]  CSV del extracto → staging statement_record (chunk, skip de inválidos)
 *          │
 * [compareStep]        FULL OUTER JOIN + clasificación de dominio → reconciliation_detail
 * </pre>
 */
@Configuration
public class ReconciliationJobConfig {

    private static final String INSERT_STATEMENT = """
            INSERT INTO smartops.statement_record (batch_file_id, line_number, reference, amount, currency, value_date)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    @Value("${smartops.batch.import.chunk-size:500}")
    private int chunkSize;

    @Value("${smartops.batch.statement.skip-limit:1000}")
    private int skipLimit;

    @Bean
    public Job reconciliationJob(JobRepository jobRepository, Step loadStatementStep, Step compareStep,
                                 JdbcTemplate jdbc, AuditTrail audit) {
        return new JobBuilder(BatchJobLauncher.RECONCILIATION_JOB, jobRepository)
                .listener(new ReconciliationJobListener(jdbc, audit))
                .start(loadStatementStep)
                .next(compareStep)
                .build();
    }

    @Bean
    public Step loadStatementStep(JobRepository jobRepository, PlatformTransactionManager txManager,
                                  FlatFileItemReader<StatementLine> statementFileReader,
                                  ItemProcessor<StatementLine, StatementLine> statementDuplicateFilter,
                                  ItemWriter<StatementLine> statementWriter,
                                  StatementSkipListener statementSkipListener) {
        return new StepBuilder("loadStatementStep", jobRepository)
                .<StatementLine, StatementLine>chunk(chunkSize, txManager)
                .reader(statementFileReader)
                .processor(statementDuplicateFilter)
                .writer(statementWriter)
                .faultTolerant()
                .skip(FlatFileParseException.class)
                .skip(DuplicateStatementLineException.class)
                .skipLimit(skipLimit)
                .retry(TransientDataAccessException.class)
                .retryLimit(3)
                .processorNonTransactional()
                .listener(statementSkipListener)
                .build();
    }

    @Bean
    public Step compareStep(JobRepository jobRepository, PlatformTransactionManager txManager,
                            CompareTasklet compareTasklet) {
        return new StepBuilder("compareStep", jobRepository)
                .tasklet(compareTasklet, txManager)
                .build();
    }

    // ------------------------------------------------------------------ componentes de ámbito step

    @Bean
    @StepScope
    public FlatFileItemReader<StatementLine> statementFileReader(
            @Value("#{jobParameters['filePath']}") String filePath) {
        return new FlatFileItemReaderBuilder<StatementLine>()
                .name("statementFileReader")
                .resource(new FileSystemResource(filePath))
                .encoding(StandardCharsets.UTF_8.name())
                .linesToSkip(1)
                .skippedLinesCallback(new HeaderValidator(StatementLineMapper.HEADER))
                .lineMapper(new StatementLineMapper())
                .build();
    }

    @Bean
    @StepScope
    public ItemProcessor<StatementLine, StatementLine> statementDuplicateFilter() {
        Set<String> seen = new HashSet<>();
        return line -> {
            if (!seen.add(line.reference())) {
                throw new DuplicateStatementLineException(line.reference());
            }
            return line;
        };
    }

    @Bean
    @StepScope
    public StatementSkipListener statementSkipListener(JdbcTemplate jdbc,
                                                       @Value("#{jobParameters['statementFileId']}") Long fileId) {
        return new StatementSkipListener(jdbc, fileId);
    }

    @Bean
    @StepScope
    public CompareTasklet compareTasklet(JdbcTemplate jdbc, ReconciliationMatcher matcher,
                                         @Value("#{jobParameters['statementFileId']}") Long fileId,
                                         @Value("#{jobParameters['reconciliationId']}") Long reconciliationId) {
        return new CompareTasklet(jdbc, matcher, fileId, reconciliationId);
    }

    @Bean
    @StepScope
    public ItemWriter<StatementLine> statementWriter(JdbcTemplate jdbc,
                                                     @Value("#{jobParameters['statementFileId']}") Long fileId) {
        return chunk -> jdbc.batchUpdate(INSERT_STATEMENT, chunk.getItems().stream()
                .map(s -> new Object[]{fileId, s.lineNumber(), s.reference(), s.amount(), s.currency(),
                        s.valueDate()})
                .toList());
    }
}
