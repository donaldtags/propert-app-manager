package com.example.primenestprop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PrimeNestPropApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrimeNestPropApplication.class, args);
    }

}
