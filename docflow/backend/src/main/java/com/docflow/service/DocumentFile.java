package com.docflow.service;

import org.springframework.core.io.Resource;

public class DocumentFile {

    private final Resource resource;
    private final String filename;

    public DocumentFile(Resource resource, String filename) {
        this.resource = resource;
        this.filename = filename;
    }

    public Resource getResource() {
        return resource;
    }

    public String getFilename() {
        return filename;
    }
}
