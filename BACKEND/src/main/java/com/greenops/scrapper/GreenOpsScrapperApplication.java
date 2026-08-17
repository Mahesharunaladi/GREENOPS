package com.greenops.scrapper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GreenOpsScrapperApplication {

    public static void main(String[] args) {
        SpringApplication.run(GreenOpsScrapperApplication.class, args);
    }
}