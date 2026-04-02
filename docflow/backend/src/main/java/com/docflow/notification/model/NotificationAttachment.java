package com.docflow.notification.model;

public class NotificationAttachment {

    private String path;
    private String fileName;
    private String contentType;
    private boolean deleteAfterSend;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public boolean isDeleteAfterSend() {
        return deleteAfterSend;
    }

    public void setDeleteAfterSend(boolean deleteAfterSend) {
        this.deleteAfterSend = deleteAfterSend;
    }
}
