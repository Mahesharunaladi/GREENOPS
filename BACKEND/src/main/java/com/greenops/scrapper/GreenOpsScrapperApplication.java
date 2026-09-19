package com.greenops.scrapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(scanBasePackages = "com.greenops")
@ConfigurationPropertiesScan
public class GreenOpsScrapperApplication {

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    public static void main(String[] args) {
        SpringApplication.run(GreenOpsScrapperApplication.class, args);
    }
}
