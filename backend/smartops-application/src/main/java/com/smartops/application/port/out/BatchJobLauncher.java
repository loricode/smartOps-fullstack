package com.smartops.application.port.out;

/** Lanza los procesos masivos de forma asíncrona. Implementado por el módulo smartops-batch. */
public interface BatchJobLauncher {

    String IMPORT_JOB = "importOperationsJob";
    String RECONCILIATION_JOB = "reconciliationJob";

    void launchOperationsImport(long batchFileId, long batchExecutionId, String filePath, String actor);

    void launchReconciliation(long statementFileId, long reconciliationId, String filePath, String actor);
}
