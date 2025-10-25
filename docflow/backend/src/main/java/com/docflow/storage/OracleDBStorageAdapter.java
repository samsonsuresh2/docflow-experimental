package com.docflow.storage;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
@Profile("oracle-db-storage")
public class OracleDBStorageAdapter implements StorageAdapter {

    @Override
    public String store(String filename, InputStream data) {
        throw new UnsupportedOperationException("Oracle DB storage not yet implemented");
    }
}
