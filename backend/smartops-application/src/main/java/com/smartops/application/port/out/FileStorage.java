package com.smartops.application.port.out;

import java.io.InputStream;
import java.nio.file.Path;

/** Bandeja de entrada de archivos recibidos. */
public interface FileStorage {

    /** Guarda el contenido y devuelve su ubicación, tamaño y SHA-256. */
    StoredFile store(String originalName, InputStream content);

    void discard(Path path);

    record StoredFile(Path path, long sizeBytes, String checksum) { }
}
