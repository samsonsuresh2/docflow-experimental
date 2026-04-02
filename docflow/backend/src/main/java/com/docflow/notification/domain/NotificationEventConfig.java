package com.docflow.notification.domain;

import com.docflow.domain.BooleanToYNConverter;
import com.docflow.notification.model.NotificationChannelType;
import com.docflow.notification.model.NotificationEventCode;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;

import java.sql.Types;

@Entity
@Table(name = "notification_event_config")
public class NotificationEventConfig {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "event_code", nullable = false, length = 100)
    private NotificationEventCode eventCode;

    @Column(name = "module_name", nullable = false, length = 100)
    private String moduleName;

    @Convert(converter = BooleanToYNConverter.class)
    @JdbcTypeCode(Types.CHAR)
    @Column(name = "is_enabled", nullable = false, length = 1, columnDefinition = "CHAR(1)")
    private Boolean enabled = Boolean.TRUE;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_type", nullable = false, length = 50)
    private NotificationChannelType channelType = NotificationChannelType.EMAIL;

    @Column(name = "template_code", length = 100)
    private String templateCode;

    @Convert(converter = BooleanToYNConverter.class)
    @JdbcTypeCode(Types.CHAR)
    @Column(name = "send_for_bulk", nullable = false, length = 1, columnDefinition = "CHAR(1)")
    private Boolean sendForBulk = Boolean.FALSE;

    public NotificationEventCode getEventCode() {
        return eventCode;
    }

    public void setEventCode(NotificationEventCode eventCode) {
        this.eventCode = eventCode;
    }

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public NotificationChannelType getChannelType() {
        return channelType;
    }

    public void setChannelType(NotificationChannelType channelType) {
        this.channelType = channelType;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public void setTemplateCode(String templateCode) {
        this.templateCode = templateCode;
    }

    public Boolean getSendForBulk() {
        return sendForBulk;
    }

    public void setSendForBulk(Boolean sendForBulk) {
        this.sendForBulk = sendForBulk;
    }
}
