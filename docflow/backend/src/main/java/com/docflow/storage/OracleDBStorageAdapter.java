package com.docflow.storage;

import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.NoSuchElementException;

@Component
@Profile("oracle-db-storage")
public class OracleDBStorageAdapter implements StorageAdapter {

    @Override
    public String store(String filename, InputStream data) {
        // TODO: Persist file content as BLOB in Oracle database
        throw new UnsupportedOperationException("Oracle DB storage not yet implemented");
    }

    @Override
    public Resource loadAsResource(String storedPath) {
        /*Path file = root.resolve(resolveRelativePath(storedPath)).normalize();
        try {
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            }
        } catch (MalformedURLException ex) {
            throw new IllegalStateException("Failed to load file", ex);
        }
        throw new NoSuchElementException("File not found");*/
        //To_DO implementation pending for DB Storage below dummy implementation
        return new Resource() {
            @Override
            public boolean exists() {
                return false;
            }

            @Override
            public URL getURL() throws IOException {
                return null;
            }

            @Override
            public URI getURI() throws IOException {
                return null;
            }

            @Override
            public File getFile() throws IOException {
                return null;
            }

            @Override
            public long contentLength() throws IOException {
                return 0;
            }

            @Override
            public long lastModified() throws IOException {
                return 0;
            }

            @Override
            public Resource createRelative(String relativePath) throws IOException {
                return null;
            }

            @Override
            public String getFilename() {
                return "";
            }

            @Override
            public String getDescription() {
                return "";
            }

            @Override
            public InputStream getInputStream() throws IOException {
                return null;
            }
        };
    }
}
