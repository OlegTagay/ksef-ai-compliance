package com.bsg6;

import com.bsg6.model.AuthTokensPair;
import com.bsg6.service.auth.AuthService;
import com.bsg6.service.invoice.InvoiceService;
import com.bsg6.service.session.OnlineSessionService;
import jakarta.xml.bind.JAXBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import pl.akmf.ksef.sdk.api.DefaultKsefClient;
import pl.akmf.ksef.sdk.api.services.DefaultCryptographyService;
import pl.akmf.ksef.sdk.client.model.ApiException;
import pl.akmf.ksef.sdk.client.model.session.*;
import pl.akmf.ksef.sdk.client.model.session.online.OpenOnlineSessionResponse;

import java.io.IOException;
import java.lang.reflect.Method;

/**
 * Base class for KSeF integration tests providing common infrastructure and utility methods.
 * <p>
 * This class extends Spring's TestNG integration and provides:
 * - Autowired KSeF SDK services and clients
 * - Common utility methods for authentication and session management
 * - Test lifecycle management with before/after hooks
 * - Logging infrastructure for test execution
 * </p>
 */
@SpringBootTest(classes = TestApplication.class)
public abstract class KsefBaseIntegrationTest extends AbstractTestNGSpringContextTests {

    protected static final Logger log = LoggerFactory.getLogger(KsefBaseIntegrationTest.class);

    // ==================== Core KSeF Services ====================

    /** Main KSeF API client for all operations */
    @Autowired
    protected DefaultKsefClient ksefClient;

    /** Service for authentication operations */
    @Autowired
    protected AuthService authService;

    /** Service for cryptographic operations (encryption, hashing, metadata) */
    @Autowired
    protected DefaultCryptographyService defaultCryptographyService;

    /** Service for managing online sessions */
    @Autowired
    protected OnlineSessionService onlineSessionService;

    /** Service for invoice operations */
    @Autowired
    protected InvoiceService invoiceService;
}

