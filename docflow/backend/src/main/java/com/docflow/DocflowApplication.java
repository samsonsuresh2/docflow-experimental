package com.docflow;

import com.docflow.service.SchemaBindingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(SchemaBindingProperties.class)
public class DocflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocflowApplication.class, args);
    }
}
