package com.smartops.application.intake;

import com.smartops.application.port.out.AuditTrail;
import com.smartops.application.port.out.BatchJobLauncher;
import com.smartops.application.port.out.BatchRegistry;
import com.smartops.application.port.out.BatchRegistry.DuplicateFileException;
import com.smartops.application.port.out.BatchRegistry.FileKind;
import com.smartops.application.port.out.FileStorage;
import com.smartops.application.port.out.FileStorage.StoredFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileIntakeServiceTest {

    private static final Path PATH = Path.of("inbox", "ops.csv");
    private static final StoredFile STORED = new StoredFile(PATH, 120, "abc123");

    @Mock
    private FileStorage storage;
    @Mock
    private BatchRegistry registry;
    @Mock
    private BatchJobLauncher launcher;
    @Mock
    private AuditTrail audit;
    @InjectMocks
    private FileIntakeService service;

    private final InputStream content = new ByteArrayInputStream("x".getBytes());

    @Test
    void registersFileAndLaunchesImport() {
        when(storage.store("ops.csv", content)).thenReturn(STORED);
        when(registry.findFileByChecksum(FileKind.OPERATIONS, "abc123")).thenReturn(Optional.empty());
        when(registry.registerFile(eq(FileKind.OPERATIONS), eq("ops.csv"), eq("abc123"), eq(120L), anyString(),
                eq("operator"))).thenReturn(10L);
        when(registry.startBatchExecution(10L, BatchJobLauncher.IMPORT_JOB, "operator")).thenReturn(55L);

        FileIntakeService.IntakeResult result = service.submitOperationsFile("ops.csv", content, "operator");

        assertThat(result).isEqualTo(new FileIntakeService.IntakeResult(10L, 55L));
        verify(launcher).launchOperationsImport(10L, 55L, PATH.toString(), "operator");
    }

    @Test
    void rejectsAlreadyReceivedFileAndDiscardsCopy() {
        when(storage.store("ops.csv", content)).thenReturn(STORED);
        when(registry.findFileByChecksum(FileKind.OPERATIONS, "abc123")).thenReturn(Optional.of(3L));

        assertThatThrownBy(() -> service.submitOperationsFile("ops.csv", content, "operator"))
                .isInstanceOf(DuplicateFileException.class)
                .extracting(e -> ((DuplicateFileException) e).getExistingFileId()).isEqualTo(3L);

        verify(storage).discard(PATH);
        verify(launcher, never()).launchOperationsImport(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void marksExecutionFailedWhenLaunchFails() {
        when(storage.store(any(), any())).thenReturn(STORED);
        when(registry.findFileByChecksum(any(), any())).thenReturn(Optional.empty());
        when(registry.registerFile(any(), any(), any(), anyLong(), any(), any())).thenReturn(10L);
        when(registry.startReconciliation(10L, "operator")).thenReturn(77L);
        doThrow(new IllegalStateException("pool lleno")).when(launcher)
                .launchReconciliation(anyLong(), anyLong(), anyString(), anyString());

        assertThatThrownBy(() -> service.submitStatementFile("stmt.csv", content, "operator"))
                .isInstanceOf(IllegalStateException.class);
        verify(registry).failReconciliation(eq(77L), org.mockito.ArgumentMatchers.contains("pool lleno"));
    }
}
