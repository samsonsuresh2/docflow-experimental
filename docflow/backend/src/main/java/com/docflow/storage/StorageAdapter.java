package com.docflow.storage;

import java.io.InputStream;

public interface StorageAdapter {
    String store(String filename, InputStream data);
}
