package com.docflow.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class NotificationTeamMappingId implements Serializable {

    @Column(name = "field_name", nullable = false, length = 200)
    private String fieldName;

    @Column(name = "field_value", nullable = false, length = 400)
    private String fieldValue;

    public NotificationTeamMappingId() {
    }

    public NotificationTeamMappingId(String fieldName, String fieldValue) {
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getFieldValue() {
        return fieldValue;
    }

    public void setFieldValue(String fieldValue) {
        this.fieldValue = fieldValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NotificationTeamMappingId that)) {
            return false;
        }
        return Objects.equals(fieldName, that.fieldName) && Objects.equals(fieldValue, that.fieldValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldName, fieldValue);
    }
}
