package com.bsg6;

import com.bsg6.config.KsefConfiguration;
import com.bsg6.service.auth.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import pl.akmf.ksef.sdk.api.DefaultKsefClient;
import pl.akmf.ksef.sdk.api.services.DefaultCryptographyService;
import pl.akmf.ksef.sdk.client.interfaces.*;
import pl.akmf.ksef.sdk.client.model.session.EncryptionData;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

@SpringBootTest(classes = {KsefConfiguration.class, AuthService.class} )
@ComponentScan(basePackages = "com.bsg6")
public abstract class KsefBaseIntegrationTest extends AbstractTestNGSpringContextTests {

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected CertificateService certificateService;

    @Autowired
    protected QrCodeService qrCodeService;

    @Autowired
    protected SignatureService signatureService;

    @Autowired
    protected VerificationLinkService verificationLinkService;

    @Autowired
    protected DefaultKsefClient ksefClient;

    @Autowired
    protected AuthService authService;

    @Autowired
    protected DefaultCryptographyService defaultCryptographyService;

    protected byte[] readBytesFromPath(String path) throws IOException {
        byte[] fileBytes;
        try (InputStream is = KsefBaseIntegrationTest.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new FileNotFoundException();
            }
            fileBytes = is.readAllBytes();
        }
        return fileBytes;
    }
}

