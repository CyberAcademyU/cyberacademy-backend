package com.cyberacademy.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = MetricsHttpTest.TestApp.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.servlet.context-path=", "management.otlp.metrics.export.enabled=false"})
@AutoConfigureObservability
class MetricsHttpTest {
    @Autowired TestRestTemplate http;
    @Autowired MeterRegistry metrics;

    @Test
    void records429EvenWhenActuatorEndpointsAreDisabled() {
        assertThat(http.getForEntity("/test-rate-limit", String.class).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        var timer = metrics.find("http.server.requests").tag("status", "429").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.getId().getTag("application")).isEqualTo("cyberacademy-backend");
        assertThat(http.getForEntity("/actuator/metrics", String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, SecurityAutoConfiguration.class,
            ManagementWebSecurityAutoConfiguration.class})
    static class TestApp {
        @Bean TestController testController() { return new TestController(); }
    }

    @RestController
    static class TestController {
        @GetMapping("/test-rate-limit")
        ResponseEntity<Void> rateLimited() { return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build(); }
    }
}
