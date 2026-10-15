package com.pharmasafe;

import com.pharmasafe.config.DotenvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PharmaSafeApplication {

    public static void main(String[] args) {
        // Automatically discover and load configuration from local .env
        DotenvLoader.load();
        SpringApplication.run(PharmaSafeApplication.class, args);
    }
}

