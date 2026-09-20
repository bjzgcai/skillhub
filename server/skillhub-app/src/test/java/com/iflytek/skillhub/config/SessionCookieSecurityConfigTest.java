package com.iflytek.skillhub.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.boot.env.YamlPropertySourceLoader;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionCookieSecurityConfigTest {

    @Test
    void defaultConfig_enablesSecureSessionCookies() throws IOException {
        ConfigurableEnvironment environment = loadEnvironment(List.of("application.yml"));

        assertTrue(environment.getProperty("server.servlet.session.cookie.secure", Boolean.class));
    }

    @Test
    void localProfile_disablesSecureSessionCookiesForHttpDevelopment() throws IOException {
        ConfigurableEnvironment environment = loadEnvironment(
                List.of("application-local.yml", "application.yml")
        );

        assertFalse(environment.getProperty("server.servlet.session.cookie.secure", Boolean.class));
    }

    private ConfigurableEnvironment loadEnvironment(List<String> resourceNames) throws IOException {
        ConfigurableEnvironment environment = new StandardEnvironment();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        for (String resourceName : resourceNames) {
            loader.load(resourceName, new ClassPathResource(resourceName))
                    .forEach(environment.getPropertySources()::addLast);
        }
        return environment;
    }
}
