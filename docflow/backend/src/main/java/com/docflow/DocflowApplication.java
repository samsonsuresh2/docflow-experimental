package com.docflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DocflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocflowApplication.class, args);
    }
}
