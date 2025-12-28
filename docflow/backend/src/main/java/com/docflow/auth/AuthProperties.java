package com.docflow.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "docflow.auth")
public class AuthProperties {

    /**
     * If true, any authenticated user implicitly gains MAKER access even without an entry in user_role_map.
     */
    private boolean implicitMakerEnabled = true;

    public boolean isImplicitMakerEnabled() {
        return implicitMakerEnabled;
    }

    public void setImplicitMakerEnabled(boolean implicitMakerEnabled) {
        this.implicitMakerEnabled = implicitMakerEnabled;
    }
}
