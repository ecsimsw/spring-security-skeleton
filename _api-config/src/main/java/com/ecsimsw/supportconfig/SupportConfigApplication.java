package com.ecsimsw.supportconfig;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

@EnableConfigServer
@SpringBootApplication
public class SupportConfigApplication {

    public static void main(String[] args) {
        SpringApplication.run(SupportConfigApplication.class, args);
    }

}
