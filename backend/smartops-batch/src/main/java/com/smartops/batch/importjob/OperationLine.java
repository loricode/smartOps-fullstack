package com.smartops.batch.importjob;

/** Línea cruda del archivo, con su número para trazabilidad de rechazos. */
public record OperationLine(int lineNumber, String raw) { }
