package com.docflow.service.schema;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "schema")
public class SchemaBindingProperties {

    private SchemaBindingStrategy bindingStrategy = SchemaBindingStrategy.ACTIVE_ONLY;

    public SchemaBindingStrategy getBindingStrategy() {
        return bindingStrategy;
    }

    public void setBindingStrategy(SchemaBindingStrategy bindingStrategy) {
        this.bindingStrategy = bindingStrategy;
    }
}
