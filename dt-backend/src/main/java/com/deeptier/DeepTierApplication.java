package com.deeptier;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DeepTierApplication {
    public static void main(String[] args) {
        SpringApplication.run(DeepTierApplication.class, args);
    }
}
