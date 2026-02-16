package com.quantor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class QuantorWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuantorWorkerApplication.class, args);
    }
}
