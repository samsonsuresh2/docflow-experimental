package com.docflow.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;

@Component
public class FileSystemStorageAdapter implements StorageAdapter {

    private final Path root;

    public FileSystemStorageAdapter(@Value("${docflow.storage.filesystem.root:uploads}") String root) {
        this.root = Paths.get(root);
    }

    @Override
    public String store(String filename, InputStream data) {
        LocalDate now = LocalDate.now();
        Path targetDir = root.resolve(String.valueOf(now.getYear())).resolve(String.format("%02d", now.getMonthValue()));
        try {
            Files.createDirectories(targetDir);
            Path targetFile = targetDir.resolve(filename);
            Files.copy(data, targetFile);
            return targetFile.toString();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to store file", ex);
        }
    }
}
