package com.smartops.infrastructure.storage;

import com.smartops.application.port.out.FileStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Bandeja de entrada en disco local: {inbox}/{yyyy-MM-dd}/{uuid}_{nombre}.
 * El checksum se calcula en streaming mientras se escribe (sin cargar el archivo en memoria).
 */
@Component
class LocalFileStorage implements FileStorage {

    private final Path inbox;

    LocalFileStorage(@Value("${smartops.storage.inbox:./data/inbox}") String inbox) {
        this.inbox = Path.of(inbox).toAbsolutePath().normalize();
    }

    @Override
    public StoredFile store(String originalName, InputStream content) {
        String safeName = sanitize(originalName);
        Path dir = inbox.resolve(LocalDate.now().toString());
        Path target = dir.resolve(UUID.randomUUID() + "_" + safeName);
        try {
            Files.createDirectories(dir);
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            long size;
            try (DigestInputStream in = new DigestInputStream(content, sha256);
                 OutputStream out = Files.newOutputStream(target)) {
                size = in.transferTo(out);
            }
            return new StoredFile(target, size, HexFormat.of().formatHex(sha256.digest()));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo almacenar el archivo " + safeName, e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void discard(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Evita path traversal ("../") y caracteres problemáticos en el nombre recibido. */
    static String sanitize(String name) {
        String base = name == null ? "file" : Path.of(name.replace('\\', '/')).getFileName().toString();
        String cleaned = base.replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.isBlank() || cleaned.startsWith(".") ? "file" + cleaned : cleaned;
    }
}
