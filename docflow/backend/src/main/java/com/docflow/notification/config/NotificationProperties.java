package com.docflow.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "docflow.notification")
public class NotificationProperties {

    private boolean enabled = true;
    private String userEmailDomain = "company.internal";
    private final Document document = new Document();
    private final Outbox outbox = new Outbox();
    private final Mail mail = new Mail();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUserEmailDomain() {
        return userEmailDomain;
    }

    public void setUserEmailDomain(String userEmailDomain) {
        this.userEmailDomain = userEmailDomain;
    }

    public Document getDocument() {
        return document;
    }

    public Outbox getOutbox() {
        return outbox;
    }

    public Mail getMail() {
        return mail;
    }

    public static class Document {
        private List<String> reviewerMetadataKeys = new ArrayList<>(List.of("reviewer", "reviewerId", "reviewer_user_id"));
        private List<String> approverMetadataKeys = new ArrayList<>(List.of("approver", "approverId", "approver_user_id"));

        public List<String> getReviewerMetadataKeys() {
            return reviewerMetadataKeys;
        }

        public void setReviewerMetadataKeys(List<String> reviewerMetadataKeys) {
            this.reviewerMetadataKeys = reviewerMetadataKeys != null ? new ArrayList<>(reviewerMetadataKeys) : new ArrayList<>();
        }

        public List<String> getApproverMetadataKeys() {
            return approverMetadataKeys;
        }

        public void setApproverMetadataKeys(List<String> approverMetadataKeys) {
            this.approverMetadataKeys = approverMetadataKeys != null ? new ArrayList<>(approverMetadataKeys) : new ArrayList<>();
        }
    }

    public static class Outbox {
        private boolean processingEnabled = true;
        private int batchSize = 20;
        private long fixedDelayMs = 30000L;

        public boolean isProcessingEnabled() {
            return processingEnabled;
        }

        public void setProcessingEnabled(boolean processingEnabled) {
            this.processingEnabled = processingEnabled;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public long getFixedDelayMs() {
            return fixedDelayMs;
        }

        public void setFixedDelayMs(long fixedDelayMs) {
            this.fixedDelayMs = fixedDelayMs;
        }
    }

    public static class Mail {
        private boolean enabled;
        private List<String> adapterPriority = new ArrayList<>(List.of("SMTP", "LOCALHOST_SMTP", "MAILX"));
        private String fromAddress;
        private String replyTo;
        private List<String> allowedInternalDomains = new ArrayList<>();
        private int maxToCount = 20;
        private int maxCcCount = 20;
        private int connectionTimeoutMs = 5000;
        private int readTimeoutMs = 5000;
        private int writeTimeoutMs = 5000;
        private final Smtp smtp = new Smtp();
        private final Smtp localhostRelay = new Smtp();
        private String mailxCommandPath = "mailx";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getAdapterPriority() {
            return adapterPriority;
        }

        public void setAdapterPriority(List<String> adapterPriority) {
            this.adapterPriority = adapterPriority != null ? new ArrayList<>(adapterPriority) : new ArrayList<>();
        }

        public String getFromAddress() {
            return fromAddress;
        }

        public void setFromAddress(String fromAddress) {
            this.fromAddress = fromAddress;
        }

        public String getReplyTo() {
            return replyTo;
        }

        public void setReplyTo(String replyTo) {
            this.replyTo = replyTo;
        }

        public List<String> getAllowedInternalDomains() {
            return allowedInternalDomains;
        }

        public void setAllowedInternalDomains(List<String> allowedInternalDomains) {
            this.allowedInternalDomains = allowedInternalDomains != null ? new ArrayList<>(allowedInternalDomains) : new ArrayList<>();
        }

        public int getMaxToCount() {
            return maxToCount;
        }

        public void setMaxToCount(int maxToCount) {
            this.maxToCount = maxToCount;
        }

        public int getMaxCcCount() {
            return maxCcCount;
        }

        public void setMaxCcCount(int maxCcCount) {
            this.maxCcCount = maxCcCount;
        }

        public int getConnectionTimeoutMs() {
            return connectionTimeoutMs;
        }

        public void setConnectionTimeoutMs(int connectionTimeoutMs) {
            this.connectionTimeoutMs = connectionTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }

        public int getWriteTimeoutMs() {
            return writeTimeoutMs;
        }

        public void setWriteTimeoutMs(int writeTimeoutMs) {
            this.writeTimeoutMs = writeTimeoutMs;
        }

        public Smtp getSmtp() {
            return smtp;
        }

        public Smtp getLocalhostRelay() {
            return localhostRelay;
        }

        public String getMailxCommandPath() {
            return mailxCommandPath;
        }

        public void setMailxCommandPath(String mailxCommandPath) {
            this.mailxCommandPath = mailxCommandPath;
        }
    }

    public static class Smtp {
        private String host;
        private int port;
        private boolean auth;
        private boolean startTls;
        private String username;
        private String password;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public boolean isAuth() {
            return auth;
        }

        public void setAuth(boolean auth) {
            this.auth = auth;
        }

        public boolean isStartTls() {
            return startTls;
        }

        public void setStartTls(boolean startTls) {
            this.startTls = startTls;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
