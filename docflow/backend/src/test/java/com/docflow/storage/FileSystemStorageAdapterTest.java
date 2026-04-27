package com.docflow.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileSystemStorageAdapterTest {

    @TempDir
    Path tempDir;

    @Test
    void storeWritesFileUnderRootAndLoadAcceptsStoredPath() throws Exception {
        FileSystemStorageAdapter adapter = new FileSystemStorageAdapter(tempDir.toString());

        String stored = adapter.store("nested/report.txt", new ByteArrayInputStream("hello".getBytes()));

        assertThat(stored).endsWith("/nested/report.txt");
        assertThat(Files.readString(tempDir.resolve("nested/report.txt"))).isEqualTo("hello");
        assertThat(adapter.loadAsResource(stored).getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("hello");
        assertThat(adapter.loadAsResource(tempDir.resolve("nested/report.txt").toString()).exists()).isTrue();
    }

    @Test
    void storeRejectsAbsoluteAndTraversingPaths() {
        FileSystemStorageAdapter adapter = new FileSystemStorageAdapter(tempDir.toString());

        assertThatThrownBy(() -> adapter.store(tempDir.resolve("outside.txt").toString(), new ByteArrayInputStream(new byte[0])))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("absolute");
        assertThatThrownBy(() -> adapter.store("../outside.txt", new ByteArrayInputStream(new byte[0])))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("traverse");
    }

    @Test
    void loadAsResourceRejectsMissingRootOnlyPath() {
        FileSystemStorageAdapter adapter = new FileSystemStorageAdapter(tempDir.toString());

        assertThatThrownBy(() -> adapter.loadAsResource(tempDir.getFileName().toString()))
            .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> adapter.loadAsResource("missing.txt"))
            .isInstanceOf(NoSuchElementException.class);
    }
}
