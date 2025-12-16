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

    // ==================== Test Lifecycle Hooks ====================

    /**
     * Executed before each test method.
     * Logs test start and can be overridden for custom setup.
     */
    @BeforeMethod
    protected void beforeEachTest(Method method) {
        log.info("Starting test: {}.{}", method.getDeclaringClass().getSimpleName(), method.getName());
    }

    /**
     * Executed after each test method.
     * Logs test completion and can be overridden for custom cleanup.
     */
    @AfterMethod
    protected void afterEachTest(Method method) {
        log.info("Completed test: {}.{}", method.getDeclaringClass().getSimpleName(), method.getName());
    }

    // ==================== Utility Methods ====================

    /**
     * Authenticates with KSeF using NIP and returns access token.
     *
     * @param nip The tax identification number (NIP)
     * @return Access token for subsequent API calls
     */
    protected String authenticateAndGetToken(String nip) throws JAXBException, IOException, ApiException {
        AuthTokensPair tokens = authService.authWithCustomNipAndRsa(nip);
        return tokens.accessToken();
    }

    /**
     * Opens an online session with standard FA(2) configuration.
     *
     * @param accessToken The authentication access token
     * @return The session reference number
     */
    protected String openStandardOnlineSession(String accessToken) throws ApiException {
        EncryptionData encryptionData = defaultCryptographyService.getEncryptionData();
        OpenOnlineSessionResponse response = onlineSessionService.openOnlineSession(
                encryptionData,
                SystemCode.FA_2,
                SchemaVersion.VERSION_1_0E,
                SessionValue.FA,
                accessToken
        );
        return response.getReferenceNumber();
    }

    /**
     * Performs complete session lifecycle: open, execute action, close.
     *
     * @param nip The NIP for authentication
     * @param sessionAction Action to execute within the session (receives sessionRef and accessToken)
     */
    protected void executeInSession(String nip, SessionAction sessionAction) throws Exception {
        String accessToken = authenticateAndGetToken(nip);
        String sessionRef = openStandardOnlineSession(accessToken);

        try {
            sessionAction.execute(sessionRef, accessToken);
        } finally {
            try {
                onlineSessionService.closeOnlineSession(sessionRef, accessToken);
            } catch (Exception e) {
                log.warn("Failed to close session {}: {}", sessionRef, e.getMessage());
            }
        }
    }

    /**
     * Performs session lifecycle and returns session data for post-session operations.
     * Use this when you need to access session reference or token after the session closes.
     *
     * @param nip The NIP for authentication
     * @param sessionAction Action to execute within the session
     * @return SessionData containing sessionReference and accessToken
     */
    protected SessionData executeInSessionWithData(String nip, SessionAction sessionAction) throws Exception {
        String accessToken = authenticateAndGetToken(nip);
        String sessionRef = openStandardOnlineSession(accessToken);

        try {
            sessionAction.execute(sessionRef, accessToken);
        } finally {
            try {
                onlineSessionService.closeOnlineSession(sessionRef, accessToken);
            } catch (Exception e) {
                log.warn("Failed to close session {}: {}", sessionRef, e.getMessage());
            }
        }

        return new SessionData(sessionRef, accessToken);
    }

    /**
     * Functional interface for actions to execute within a session.
     */
    @FunctionalInterface
    protected interface SessionAction {
        void execute(String sessionReference, String accessToken) throws Exception;
    }

    /**
     * Container for session data returned after session execution.
     *
     * @param sessionReference The session reference number
     * @param accessToken The access token for API calls
     */
    protected record SessionData(String sessionReference, String accessToken) {
    }

    /**
     * Retrieves encryption data for the current session.
     *
     * @return EncryptionData containing cipher key and IV
     */
    protected EncryptionData getEncryptionData() {
        return defaultCryptographyService.getEncryptionData();
    }

    /**
     * Waits for invoice processing and returns whether it was successful.
     *
     * @param sessionReference Session reference number
     * @param accessToken Access token
     * @return true if invoices were processed before timeout, false otherwise
     */
    protected boolean waitForInvoiceProcessing(String sessionReference, String accessToken) {
        return onlineSessionService.waitUntilInvoicesProcessed(sessionReference, accessToken);
    }

    /**
     * Waits for UPO generation after session close.
     *
     * @param sessionReference Session reference number
     * @param accessToken Access token
     */
    protected void waitForUpoGeneration(String sessionReference, String accessToken) {
        onlineSessionService.waitUntilUpoGenerated(sessionReference, accessToken);
    }
}

