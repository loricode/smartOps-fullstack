package com.smartops.batch.launch;

import com.smartops.application.port.out.BatchJobLauncher;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Implementa el puerto {@link BatchJobLauncher} con un JobLauncher asíncrono: la petición HTTP
 * responde 202 de inmediato y el job corre en un pool acotado (backpressure: si el pool y la
 * cola están llenos, el lanzamiento falla y la ejecución queda FAILED).
 */
@Component
public class SpringBatchJobLauncher implements BatchJobLauncher {

    private final JobLauncher launcher;
    private final Job importOperationsJob;
    private final Job reconciliationJob;

    public SpringBatchJobLauncher(@Qualifier("asyncJobLauncher") JobLauncher launcher,
                                  @Qualifier("importOperationsJob") Job importOperationsJob,
                                  @Qualifier("reconciliationJob") Job reconciliationJob) {
        this.launcher = launcher;
        this.importOperationsJob = importOperationsJob;
        this.reconciliationJob = reconciliationJob;
    }

    @Override
    public void launchOperationsImport(long batchFileId, long batchExecutionId, String filePath, String actor) {
        run(importOperationsJob, new JobParametersBuilder()
                .addLong("batchFileId", batchFileId)
                .addLong("batchExecutionId", batchExecutionId)
                .addString("filePath", filePath, false)
                .addString("actor", actor, false)
                .toJobParameters());
    }

    @Override
    public void launchReconciliation(long statementFileId, long reconciliationId, String filePath, String actor) {
        run(reconciliationJob, new JobParametersBuilder()
                .addLong("statementFileId", statementFileId)
                .addLong("reconciliationId", reconciliationId)
                .addString("filePath", filePath, false)
                .addString("actor", actor, false)
                .toJobParameters());
    }

    private void run(Job job, JobParameters parameters) {
        try {
            launcher.run(job, parameters);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo lanzar " + job.getName() + ": " + e.getMessage(), e);
        }
    }

    /**
     * El pool no se expone como bean {@code TaskExecutor}: si lo hiciera, Spring Boot dejaría de
     * crear su {@code applicationTaskExecutor} por defecto y lo reemplazaría en toda la aplicación.
     */
    @Configuration
    static class AsyncLauncherConfig implements DisposableBean {

        private final ThreadPoolTaskExecutor batchPool = new ThreadPoolTaskExecutor();

        @Bean
        JobLauncher asyncJobLauncher(JobRepository jobRepository,
                                     @Value("${smartops.batch.pool.core-size:2}") int coreSize,
                                     @Value("${smartops.batch.pool.max-size:4}") int maxSize,
                                     @Value("${smartops.batch.pool.queue-capacity:20}") int queueCapacity)
                throws Exception {
            batchPool.setCorePoolSize(coreSize);
            batchPool.setMaxPoolSize(maxSize);
            batchPool.setQueueCapacity(queueCapacity);
            batchPool.setThreadNamePrefix("batch-");
            batchPool.setWaitForTasksToCompleteOnShutdown(true);
            batchPool.setAwaitTerminationSeconds(30);
            batchPool.initialize();

            TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
            launcher.setJobRepository(jobRepository);
            launcher.setTaskExecutor(batchPool);
            launcher.afterPropertiesSet();
            return launcher;
        }

        @Override
        public void destroy() {
            batchPool.shutdown();
        }
    }
}
