package com.bsg6.config;

import com.bsg6.config.ConfigurationProps;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.akmf.ksef.sdk.api.DefaultKsefClient;
import pl.akmf.ksef.sdk.api.services.*;
import pl.akmf.ksef.sdk.client.interfaces.*;
import pl.akmf.ksef.sdk.client.model.session.EncryptionData;

import java.net.http.HttpClient;

@Configuration
public class KsefConfiguration {

    @Bean
    public ConfigurationProps ksefApiProperties() {
        return new ConfigurationProps();
    }

    @Bean
    public ObjectMapper ksefObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    @Bean
    public CertificateService certificateService() {
        return new DefaultCertificateService();
    }

    @Bean
    public SignatureService signatureService() {
        return new DefaultSignatureService();
    }

    @Bean
    public QrCodeService qrCodeService() {
        return new DefaultQrCodeService();
    }

    @Bean
    public VerificationLinkService verificationLinkService(ConfigurationProps props) {
        return new DefaultVerificationLinkService(props.getBaseUri());
    }

    @Bean
    public HttpClient ksefHttpClient(ConfigurationProps props) {
        return HttpClient.newBuilder()
                .connectTimeout(props.getRequestTimeout())
                .build();
    }

    @Bean
    public DefaultKsefClient ksefClient(HttpClient httpClient,
                                        ConfigurationProps props,
                                        ObjectMapper objectMapper) {
        return new DefaultKsefClient(httpClient, props, objectMapper);
    }

    @Bean
    public DefaultCryptographyService defaultCryptographyService(DefaultKsefClient ksefClient) {
        return new DefaultCryptographyService(ksefClient);
    }
}
