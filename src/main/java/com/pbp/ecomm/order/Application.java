package com.pbp.ecomm.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@Configuration
@EnableJpaAuditing                  // enables @CreatedDate / @LastModifiedDate
@EnableJpaRepositories(
        basePackages = "com.pbp.ecomm.order.repo"
)
@EnableTransactionManagement
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
/*
    @Bean
    public RestTemplate restTemplate(RestTemplate builder) {
        return  new RestTemplate();
    }*/
}
