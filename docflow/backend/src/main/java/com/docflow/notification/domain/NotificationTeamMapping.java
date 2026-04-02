package com.docflow.notification.domain;

import com.docflow.domain.BooleanToYNConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;

import java.sql.Types;

@Entity
@Table(name = "notification_team_mapping")
public class NotificationTeamMapping {

    @EmbeddedId
    private NotificationTeamMappingId id;

    @Column(name = "team_name", length = 200)
    private String teamName;

    @Column(name = "team_email_dl", nullable = false, length = 320)
    private String teamEmailDl;

    @Convert(converter = BooleanToYNConverter.class)
    @JdbcTypeCode(Types.CHAR)
    @Column(name = "is_active", nullable = false, length = 1, columnDefinition = "CHAR(1)")
    private Boolean active = Boolean.TRUE;

    public NotificationTeamMappingId getId() {
        return id;
    }

    public void setId(NotificationTeamMappingId id) {
        this.id = id;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public String getTeamEmailDl() {
        return teamEmailDl;
    }

    public void setTeamEmailDl(String teamEmailDl) {
        this.teamEmailDl = teamEmailDl;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
