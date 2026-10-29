package com.cyberacademy.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Carga automáticamente las variables definidas en el archivo {@code .env}
 * ubicado en la raíz del backend antes de que Spring Boot procese su propia
 * configuración ({@code application.yml} / {@code application-local.yml}).
 *
 * Se registra como {@link ApplicationContextInitializer} para ejecutarse
 * durante el arranque de Spring, antes de crear el contexto de aplicación.
 *
 * Uso:
 *   1. Crear el archivo {@code backend/.env} con pares KEY=VALOR.
 *   2. Ejecutar {@code mvn spring-boot:run} desde la carpeta {@code backend/}.
 *   3. Spring leerá automáticamente el .env sin necesidad de exportar variables
 *      manualmente en la terminal.
 */
public class DotenvLoader implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final String ENV_FILE_NAME = ".env";

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment environment = applicationContext.getEnvironment();
        Path envPath = Paths.get(ENV_FILE_NAME);

        if (!Files.exists(envPath)) {
            return; // No hay archivo .env, se usan las variables del entorno del SO
        }

        Properties props = new Properties();

        try (BufferedReader reader = new BufferedReader(new FileReader(envPath.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();

                // Ignorar líneas vacías y comentarios
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }

                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();

                // Eliminar comillas dobles si las tiene
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }

                // Solo establecer si no viene ya definida como variable de entorno del SO
                // (las variables del SO tienen prioridad sobre el .env)
                if (System.getenv(key) == null) {
                    props.setProperty(key, value);
                }
            }
        } catch (IOException e) {
            // No fallar el arranque por un problema de lectura del .env
            environment.getSystemProperties().put("dotenv.load.error", e.getMessage());
            return;
        }

        if (!props.isEmpty()) {
            MutablePropertySources sources = environment.getPropertySources();
            sources.addFirst(new PropertiesPropertySource("dotenv", props));
        }
    }
}
