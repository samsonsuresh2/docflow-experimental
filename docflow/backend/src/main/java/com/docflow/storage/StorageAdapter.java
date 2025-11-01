package com.docflow.storage;

import java.io.InputStream;

import org.springframework.core.io.Resource;

public interface StorageAdapter {
    String store(String filename, InputStream data);

    Resource loadAsResource(String storedPath);
}
