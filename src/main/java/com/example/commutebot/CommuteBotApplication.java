package com.example.commutebot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CommuteBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommuteBotApplication.class, args);
    }
}
