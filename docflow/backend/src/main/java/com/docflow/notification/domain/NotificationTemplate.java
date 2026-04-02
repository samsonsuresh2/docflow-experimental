package com.docflow.notification.domain;

import com.docflow.domain.BooleanToYNConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;

import java.sql.Types;

@Entity
@Table(name = "notification_template")
public class NotificationTemplate {

    @Id
    @Column(name = "template_code", nullable = false, length = 100)
    private String templateCode;

    @Column(name = "subject_template", nullable = false, length = 1000)
    private String subjectTemplate;

    @Lob
    @Column(name = "body_template", nullable = false)
    private String bodyTemplate;

    @Convert(converter = BooleanToYNConverter.class)
    @JdbcTypeCode(Types.CHAR)
    @Column(name = "is_html", nullable = false, length = 1, columnDefinition = "CHAR(1)")
    private Boolean html = Boolean.FALSE;

    @Convert(converter = BooleanToYNConverter.class)
    @JdbcTypeCode(Types.CHAR)
    @Column(name = "is_active", nullable = false, length = 1, columnDefinition = "CHAR(1)")
    private Boolean active = Boolean.TRUE;

    public String getTemplateCode() {
        return templateCode;
    }

    public void setTemplateCode(String templateCode) {
        this.templateCode = templateCode;
    }

    public String getSubjectTemplate() {
        return subjectTemplate;
    }

    public void setSubjectTemplate(String subjectTemplate) {
        this.subjectTemplate = subjectTemplate;
    }

    public String getBodyTemplate() {
        return bodyTemplate;
    }

    public void setBodyTemplate(String bodyTemplate) {
        this.bodyTemplate = bodyTemplate;
    }

    public Boolean getHtml() {
        return html;
    }

    public void setHtml(Boolean html) {
        this.html = html;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
