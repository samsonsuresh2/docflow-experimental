package com.docflow;

import com.docflow.service.SchemaBindingProperties;
import com.docflow.notification.config.NotificationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({SchemaBindingProperties.class, NotificationProperties.class})
public class DocflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocflowApplication.class, args);
    }
}
