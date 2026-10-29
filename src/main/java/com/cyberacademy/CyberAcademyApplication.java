package com.cyberacademy;

import com.cyberacademy.config.DotenvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CyberAcademyApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(CyberAcademyApplication.class);
        app.addInitializers(new DotenvLoader()); // Carga automática del archivo .env
        app.run(args);
    }
}