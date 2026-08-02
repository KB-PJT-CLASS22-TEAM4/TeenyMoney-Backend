package com.teenyfin.teenymoney.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertThrows;

class RuntimeConfigurationContractTest {

    @Test
    void jwtSecretMustBeExplicitlyConfigured() throws IOException {
        PropertySourcesPropertyResolver resolver = applicationPropertyResolver();

        assertThrows(IllegalArgumentException.class,
                () -> resolver.getRequiredProperty("jwt.secret"));
    }

    @Test
    void cookieSecurityMustBeExplicitlyConfigured() throws IOException {
        PropertySourcesPropertyResolver resolver = applicationPropertyResolver();

        assertThrows(IllegalArgumentException.class,
                () -> resolver.getRequiredProperty("cookie.secure"));
    }

    private PropertySourcesPropertyResolver applicationPropertyResolver() throws IOException {
        Properties properties = PropertiesLoaderUtils.loadProperties(
                new ClassPathResource("application.properties"));
        MutablePropertySources propertySources = new MutablePropertySources();
        propertySources.addLast(new PropertiesPropertySource("application", properties));
        return new PropertySourcesPropertyResolver(propertySources);
    }
}
