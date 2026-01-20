package com.docflow.reports.service;

public class DuplicateTemplateNameException extends RuntimeException {

    public DuplicateTemplateNameException(String message, Throwable cause) {
        super(message, cause);
    }
}
