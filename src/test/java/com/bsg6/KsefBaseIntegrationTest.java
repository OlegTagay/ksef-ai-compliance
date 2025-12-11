package com.bsg6;

import com.bsg6.config.KsefConfiguration;
import com.bsg6.service.auth.AuthService;
import com.bsg6.service.invoice.InvoiceService;
import com.bsg6.service.session.OnlineSessionService;
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

@SpringBootTest(classes = TestApplication.class)
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

    @Autowired
    protected OnlineSessionService onlineSessionService;

    @Autowired
    protected InvoiceService invoiceService;

}

