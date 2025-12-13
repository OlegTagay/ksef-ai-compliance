package com.bsg6.config;

import pl.akmf.ksef.sdk.api.KsefApiProperties;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;

public class ConfigurationProps extends KsefApiProperties {

    private final String baseUri;
    private final Map<String, String> defaultHeaders;
    private final Duration requestTimeout;

    public  Properties load() {
        try (InputStream in = ConfigurationProps.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in == null) {
                throw new RuntimeException("application.properties not found");
            }
            Properties props = new Properties();
            props.load(in);
            return props;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load application.properties", e);
        }
    }

    public ConfigurationProps() {
        Properties props = this.load();
        this.baseUri = URI.create(props.getProperty("KSEF_API")).toString();
        this.defaultHeaders = Map.of(
                "KSeF-Token", props.getProperty("KSEF_TOKEN"),
                "Accept", "application/json"
        );
        this.requestTimeout = Duration.ofSeconds(30);
    }

    public String getBaseUri() {
        return baseUri;
    }

    public Map<String, String> getDefaultHeaders() {
        return defaultHeaders;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }
}
