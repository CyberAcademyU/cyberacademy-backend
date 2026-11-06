package com.cyberacademy.config;

import io.micrometer.registry.otlp.OtlpMeterRegistry;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.otlp.OtlpMetricsExportAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.otlp.OtlpProperties;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class GrafanaMetricsConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(context -> {
                try {
                    var sources = new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
                    sources.forEach(context.getEnvironment().getPropertySources()::addLast);
                } catch (java.io.IOException ex) {
                    throw new IllegalStateException(ex);
                }
            })
            .withConfiguration(AutoConfigurations.of(MetricsAutoConfiguration.class, OtlpMetricsExportAutoConfiguration.class));

    @Test
    void exportAndManagementEndpointsAreDisabledByDefault() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(OtlpMeterRegistry.class);
            assertThat(context.getEnvironment().getProperty("management.endpoints.enabled-by-default")).isEqualTo("false");
            assertThat(context.getEnvironment().getProperty("management.endpoints.web.exposure.exclude")).isEqualTo("*");
        });
    }

    @Test
    void exportUsesConfiguredEndpointAndAuthorizationWhenEnabled() {
        runner.withPropertyValues(
                "GRAFANA_METRICS_ENABLED=true",
                "GRAFANA_OTLP_METRICS_URL=https://metrics.example.com/otlp/v1/metrics",
                "GRAFANA_OTLP_AUTHORIZATION=Basic test-credential"
        ).run(context -> {
            assertThat(context).hasSingleBean(OtlpMeterRegistry.class);
            OtlpProperties properties = context.getBean(OtlpProperties.class);
            assertThat(properties.getUrl()).isEqualTo("https://metrics.example.com/otlp/v1/metrics");
            assertThat(properties.getHeaders()).containsEntry("Authorization", "Basic test-credential");
            assertThat(properties.getResourceAttributes()).containsEntry("service.name", "cyberacademy-backend");
        });
    }

    @Test
    void exportsMetricsOverHttpWithConfiguredAuthorization() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<Integer> size = new AtomicReference<>(0);
        server.createContext("/otlp/v1/metrics", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            size.set(exchange.getRequestBody().readAllBytes().length);
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            received.countDown();
        });
        server.start();
        try {
            runner.withPropertyValues(
                    "GRAFANA_METRICS_ENABLED=true",
                    "GRAFANA_OTLP_METRICS_URL=http://127.0.0.1:" + server.getAddress().getPort() + "/otlp/v1/metrics",
                    "GRAFANA_OTLP_AUTHORIZATION=Basic test-credential",
                    "management.otlp.metrics.export.step=1s"
            ).run(context -> {
                context.getBean(OtlpMeterRegistry.class).counter("cyberacademy.test").increment();
                assertThat(received.await(8, TimeUnit.SECONDS)).isTrue();
                assertThat(authorization.get()).isEqualTo("Basic test-credential");
                assertThat(size.get()).isPositive();
            });
        } finally {
            server.stop(0);
        }
    }
}
