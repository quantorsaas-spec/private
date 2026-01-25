package com.quantor.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.quantor")
@EnableJpaRepositories(basePackages = "com.quantor")
@EntityScan(basePackages = "com.quantor")
@ConfigurationPropertiesScan(basePackages = "com.quantor")
public class QuantorApiApplication {
  public static void main(String[] args) {
    SpringApplication.run(QuantorApiApplication.class, args);
  }
}
