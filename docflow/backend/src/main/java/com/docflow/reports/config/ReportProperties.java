package com.docflow.reports.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "docflow.reports")
public class ReportProperties {

    private DocumentTableProperties documentTable = new DocumentTableProperties();
    private MetadataTableProperties metadataTable = new MetadataTableProperties();
    private List<EntityProperties> entities = new ArrayList<>();

    public DocumentTableProperties getDocumentTable() {
        return documentTable;
    }

    public void setDocumentTable(DocumentTableProperties documentTable) {
        this.documentTable = documentTable;
    }

    public MetadataTableProperties getMetadataTable() {
        return metadataTable;
    }

    public void setMetadataTable(MetadataTableProperties metadataTable) {
        this.metadataTable = metadataTable;
    }

    public List<EntityProperties> getEntities() {
        return entities;
    }

    public void setEntities(List<EntityProperties> entities) {
        this.entities = entities != null ? entities : new ArrayList<>();
    }

    public List<EntityProperties> getEnabledEntities() {
        if (entities == null) {
            return List.of();
        }
        List<EntityProperties> safe = new ArrayList<>();
        for (EntityProperties entity : entities) {
            if (entity != null && entity.getName() != null && !entity.getName().isBlank()) {
                safe.add(entity);
            }
        }
        return Collections.unmodifiableList(safe);
    }

    public static class DocumentTableProperties {
        private String name;
        private String internalPk;
        private String businessKey;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getInternalPk() {
            return internalPk;
        }

        public void setInternalPk(String internalPk) {
            this.internalPk = internalPk;
        }

        public String getBusinessKey() {
            return businessKey;
        }

        public void setBusinessKey(String businessKey) {
            this.businessKey = businessKey;
        }
    }

    public static class MetadataTableProperties {
        private String name;
        private String documentIdColumn;
        private String keyColumn;
        private String valueColumn;
        private boolean valueIsClob;
        private int clobSelectLimit = 4000;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDocumentIdColumn() {
            return documentIdColumn;
        }

        public void setDocumentIdColumn(String documentIdColumn) {
            this.documentIdColumn = documentIdColumn;
        }

        public String getKeyColumn() {
            return keyColumn;
        }

        public void setKeyColumn(String keyColumn) {
            this.keyColumn = keyColumn;
        }

        public String getValueColumn() {
            return valueColumn;
        }

        public void setValueColumn(String valueColumn) {
            this.valueColumn = valueColumn;
        }

        public boolean isValueIsClob() {
            return valueIsClob;
        }

        public void setValueIsClob(boolean valueIsClob) {
            this.valueIsClob = valueIsClob;
        }

        public int getClobSelectLimit() {
            return clobSelectLimit;
        }

        public void setClobSelectLimit(int clobSelectLimit) {
            this.clobSelectLimit = clobSelectLimit;
        }
    }

    public static class EntityProperties {
        private String name;
        private String label;
        private String type;
        private JoinProperties joinToDocument = new JoinProperties();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public JoinProperties getJoinToDocument() {
            return joinToDocument;
        }

        public void setJoinToDocument(JoinProperties joinToDocument) {
            this.joinToDocument = joinToDocument;
        }
    }

    public static class JoinProperties {
        private boolean enabled;
        private String businessFkColumn;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBusinessFkColumn() {
            return businessFkColumn;
        }

        public void setBusinessFkColumn(String businessFkColumn) {
            this.businessFkColumn = businessFkColumn;
        }
    }
}
