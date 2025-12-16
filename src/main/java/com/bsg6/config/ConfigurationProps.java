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

    // Authentication configuration
    private final String defaultCompanyName;
    private final String defaultCompanySubject;
    private final String defaultPersonGivenName;
    private final String defaultPersonSurname;
    private final String defaultPersonIdentifier;
    private final Duration authPollingTimeout;
    private final Duration authPollingInterval;

    // Session configuration
    private final Duration sessionProcessingTimeout;
    private final Duration sessionProcessingInterval;
    private final Duration sessionUpoTimeout;
    private final Duration sessionUpoInterval;
    private final Duration batchStatusTimeout;
    private final Duration batchStatusInterval;

    // Invoice configuration
    private final String defaultInvoiceTemplatePath;

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

        // Initialize authentication configuration
        this.defaultCompanyName = props.getProperty("ksef.auth.default.company.name");
        this.defaultCompanySubject = props.getProperty("ksef.auth.default.company.subject");
        this.defaultPersonGivenName = props.getProperty("ksef.auth.default.person.given.name");
        this.defaultPersonSurname = props.getProperty("ksef.auth.default.person.surname");
        this.defaultPersonIdentifier = props.getProperty("ksef.auth.default.person.identifier");
        this.authPollingTimeout = Duration.ofSeconds(Long.parseLong(props.getProperty("ksef.auth.polling.timeout.seconds")));
        this.authPollingInterval = Duration.ofSeconds(Long.parseLong(props.getProperty("ksef.auth.polling.interval.seconds")));

        // Initialize session configuration
        this.sessionProcessingTimeout = Duration.ofSeconds(Long.parseLong(props.getProperty("ksef.session.processing.timeout.seconds")));
        this.sessionProcessingInterval = Duration.ofSeconds(Long.parseLong(props.getProperty("ksef.session.processing.interval.seconds")));
        this.sessionUpoTimeout = Duration.ofSeconds(Long.parseLong(props.getProperty("ksef.session.upo.timeout.seconds")));
        this.sessionUpoInterval = Duration.ofSeconds(Long.parseLong(props.getProperty("ksef.session.upo.interval.seconds")));
        this.batchStatusTimeout = Duration.ofSeconds(Long.parseLong(props.getProperty("ksef.session.batch.status.timeout.seconds")));
        this.batchStatusInterval = Duration.ofSeconds(Long.parseLong(props.getProperty("ksef.session.batch.status.interval.seconds")));

        // Initialize invoice configuration
        this.defaultInvoiceTemplatePath = props.getProperty("ksef.invoice.template.default");
    }

    public String getBaseUri() {
        return baseUri;
    }

    @Override
    public String getQrUri() {
        return "";
    }

    public Map<String, String> getDefaultHeaders() {
        return defaultHeaders;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    // Authentication configuration getters
    public String getDefaultCompanyName() {
        return defaultCompanyName;
    }

    public String getDefaultCompanySubject() {
        return defaultCompanySubject;
    }

    public String getDefaultPersonGivenName() {
        return defaultPersonGivenName;
    }

    public String getDefaultPersonSurname() {
        return defaultPersonSurname;
    }

    public String getDefaultPersonIdentifier() {
        return defaultPersonIdentifier;
    }

    public Duration getAuthPollingTimeout() {
        return authPollingTimeout;
    }

    public Duration getAuthPollingInterval() {
        return authPollingInterval;
    }

    // Session configuration getters
    public Duration getSessionProcessingTimeout() {
        return sessionProcessingTimeout;
    }

    public Duration getSessionProcessingInterval() {
        return sessionProcessingInterval;
    }

    public Duration getSessionUpoTimeout() {
        return sessionUpoTimeout;
    }

    public Duration getSessionUpoInterval() {
        return sessionUpoInterval;
    }

    public Duration getBatchStatusTimeout() {
        return batchStatusTimeout;
    }

    public Duration getBatchStatusInterval() {
        return batchStatusInterval;
    }

    // Invoice configuration getters
    public String getDefaultInvoiceTemplatePath() {
        return defaultInvoiceTemplatePath;
    }
}
