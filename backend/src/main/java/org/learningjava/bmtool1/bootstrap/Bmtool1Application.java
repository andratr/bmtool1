package org.learningjava.bmtool1.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "org.learningjava.bmtool1")
public class Bmtool1Application {
    public static void main(String[] args) {
        SpringApplication.run(Bmtool1Application.class, args);
    }
}