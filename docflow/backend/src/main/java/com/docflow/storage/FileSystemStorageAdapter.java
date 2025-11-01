package com.docflow.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.NoSuchElementException;

@Component
public class FileSystemStorageAdapter implements StorageAdapter {

    private final Path root;
    private final String rootPrefix;

    public FileSystemStorageAdapter(@Value("${docflow.storage.filesystem.root:uploads}") String root) {
        this.root = Paths.get(root).normalize();
        Path fileName = this.root.getFileName();
        this.rootPrefix = fileName != null ? fileName.toString() : this.root.toString();
    }

    @Override
    public String store(String relativePath, InputStream data) {
        Path relative = Paths.get(relativePath).normalize();
        if (relative.isAbsolute()) {
            throw new IllegalArgumentException("Relative path must not be absolute");
        }
        for (Path name : relative) {
            if ("..".equals(name.toString())) {
                throw new IllegalArgumentException("Relative path must not traverse outside root");
            }
        }
        Path targetFile = root.resolve(relative);
        try {
            if (targetFile.getParent() != null) {
                Files.createDirectories(targetFile.getParent());
            }
            Files.copy(data, targetFile, StandardCopyOption.REPLACE_EXISTING);
            String normalizedRelative = relative.toString().replace('\\', '/');
            return rootPrefix + "/" + normalizedRelative;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to store file", ex);
        }
    }

    @Override
    public Resource loadAsResource(String storedPath) {
        Path file = root.resolve(resolveRelativePath(storedPath)).normalize();
        try {
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            }
        } catch (MalformedURLException ex) {
            throw new IllegalStateException("Failed to load file", ex);
        }
        throw new NoSuchElementException("File not found");
    }

    private Path resolveRelativePath(String storedPath) {
        Path candidate = Paths.get(storedPath).normalize();
        if (candidate.isAbsolute()) {
            if (candidate.startsWith(root)) {
                return root.relativize(candidate);
            }
            return candidate;
        }
        if (candidate.getNameCount() > 0 && candidate.getName(0).toString().equals(rootPrefix)) {
            if (candidate.getNameCount() == 1) {
                throw new NoSuchElementException("File not found");
            }
            return candidate.subpath(1, candidate.getNameCount());
        }
        return candidate;
    }
}
