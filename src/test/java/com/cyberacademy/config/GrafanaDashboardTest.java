package com.cyberacademy.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GrafanaDashboardTest {
    @Test
    void dashboardIsValidJsonWithDatasourceAndPanels() throws Exception {
        var dashboard = new ObjectMapper().readTree(Path.of("observability", "grafana-cyberacademy.json").toFile());
        assertThat(dashboard.path("__inputs").get(0).path("pluginId").asText()).isEqualTo("prometheus");
        assertThat(dashboard.path("panels").size()).isEqualTo(7);
        dashboard.path("panels").forEach(panel -> {
            assertThat(panel.path("datasource").path("uid").asText()).isEqualTo("${DS_PROMETHEUS}");
            assertThat(panel.path("targets").isEmpty()).isFalse();
        });
    }
}
