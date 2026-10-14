package com.smartbanking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SmartBankingApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartBankingApplication.class, args);
    }
}
