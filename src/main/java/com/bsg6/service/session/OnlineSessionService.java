package com.bsg6.service.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pl.akmf.ksef.sdk.api.DefaultKsefClient;
import pl.akmf.ksef.sdk.api.builders.session.OpenOnlineSessionRequestBuilder;
import pl.akmf.ksef.sdk.client.model.ApiException;
import pl.akmf.ksef.sdk.client.model.UpoVersion;
import pl.akmf.ksef.sdk.client.model.session.*;
import pl.akmf.ksef.sdk.client.model.session.online.OpenOnlineSessionRequest;
import pl.akmf.ksef.sdk.client.model.session.online.OpenOnlineSessionResponse;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

@Service
public class OnlineSessionService {

    private static final Logger log = LoggerFactory.getLogger(OnlineSessionService.class);
    private final DefaultKsefClient ksefClient;
    private final com.bsg6.config.ConfigurationProps config;

    public OnlineSessionService(DefaultKsefClient ksefClient, com.bsg6.config.ConfigurationProps config) {
        this.ksefClient = ksefClient;
        this.config = config;
    }

    /**
     * Open FA(2) online session.
     */
    public OpenOnlineSessionResponse openOnlineSession(
            EncryptionData encryptionData,
            SystemCode systemCode,
            SchemaVersion schemaVersion,
            SessionValue value,
            String accessToken) throws ApiException {

        OpenOnlineSessionRequest request = new OpenOnlineSessionRequestBuilder()
                .withFormCode(new FormCode(systemCode, schemaVersion, value))
                .withEncryptionInfo(encryptionData.encryptionInfo())
                .build();

        OpenOnlineSessionResponse response = ksefClient.openOnlineSession(request, UpoVersion.UPO_4_3,accessToken);

        if (response == null || response.getReferenceNumber() == null) {
            throw new IllegalStateException("KSeF returned no session reference number.");
        }

        return response;
    }

    /**
     * Wait until all invoices in the session are processed.
     */
    public boolean waitUntilInvoicesProcessed(String sessionReference, String accessToken) {
        try {
            await().atMost(config.getSessionProcessingTimeout())
                    .pollInterval(config.getSessionProcessingInterval())
                    .until(() -> isInvoicesInSessionProcessed(sessionReference, accessToken));

            return true; // processed before timeout
        } catch (Exception e) {
            return false; // timeout or unexpected error
        }
    }

    /**
     * Close the online session.
     */
    public void closeOnlineSession(String sessionReference, String accessToken) throws ApiException {
        ksefClient.closeOnlineSession(sessionReference, accessToken);
    }

    public void closeBatchSession(String sessionReference, String accessToken) throws ApiException {
        ksefClient.closeBatchSession(sessionReference, accessToken);
    }

    /**
     * Wait for UPO to be generated.
     */
    public void waitUntilUpoGenerated(String sessionReference, String accessToken) {
        await().atMost(config.getSessionUpoTimeout())
                .pollInterval(config.getSessionUpoInterval())
                .until(() -> isUpoGenerated(sessionReference, accessToken));
    }

    /**
     * Get documents produced by the session (contains KSeF number).
     */
    public SessionInvoiceStatusResponse getSessionDocuments(
            String sessionReference, String accessToken) throws ApiException {
        return getOnlineSessionDocuments(sessionReference, accessToken);
    }

    /**
     * Get UPO reference after session close.
     */
    public UpoPageResponse getUpoReferenceAfterClose(String sessionReference, String accessToken) throws ApiException {
        return getOnlineSessionUpoAfterCloseSession(sessionReference, accessToken);
    }

    /**
     * Download UPO document (binary XML).
     */
    public byte[] downloadUpo(String sessionReference, String upoReference, String accessToken) throws ApiException {
        return getOnlineSessionUpo(sessionReference, upoReference, accessToken);
    }

    private boolean isInvoicesInSessionProcessed(String sessionReferenceNumber, String accessToken) {
        try {
            SessionStatusResponse statusResponse = ksefClient.getSessionStatus(sessionReferenceNumber, accessToken);
            return statusResponse != null &&
                    statusResponse.getSuccessfulInvoiceCount() != null &&
                    statusResponse.getSuccessfulInvoiceCount() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isUpoGenerated(String sessionReferenceNumber, String accessToken) {
        try {
            SessionStatusResponse statusResponse = ksefClient.getSessionStatus(sessionReferenceNumber, accessToken);
            return statusResponse != null && statusResponse.getStatus().getCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    public UpoPageResponse getOnlineSessionUpoAfterCloseSession(String sessionReferenceNumber, String accessToken) throws ApiException {
        SessionStatusResponse statusResponse = ksefClient.getSessionStatus(sessionReferenceNumber, accessToken);

        return statusResponse.getUpo().getPages().getFirst();
    }

    public SessionInvoiceStatusResponse getOnlineSessionDocuments(String sessionReferenceNumber, String accessToken) throws ApiException {
        SessionInvoicesResponse sessionInvoices = ksefClient.getSessionInvoices(sessionReferenceNumber, null, 10, accessToken);

        return sessionInvoices.getInvoices().getFirst();
    }

    public byte[] getOnlineSessionInvoiceUpo(String sessionReferenceNumber, String ksefNumber, String accessToken) throws ApiException {
        log.debug("getOnlineSessionInvoiceUpo: sessionReferenceNumber: {}, ksefNumber: {}", sessionReferenceNumber, ksefNumber);

        return ksefClient.getSessionInvoiceUpoByKsefNumber(sessionReferenceNumber, ksefNumber, accessToken);
    }

    public byte[] getOnlineSessionInvoiceUpoByInvoiceReferenceNumber(String sessionReferenceNumber, String invoiceReferenceNumber, String accessToken) throws ApiException {

        return ksefClient.getSessionInvoiceUpoByReferenceNumber(sessionReferenceNumber, invoiceReferenceNumber, accessToken);
    }

    public byte[] getOnlineSessionUpo(String sessionReferenceNumber, String upoReferenceNumber, String accessToken) throws ApiException {

        return ksefClient.getSessionUpo(sessionReferenceNumber, upoReferenceNumber, accessToken);
    }

    public SessionStatusResponse getBatchSessionStatus(String referenceNumber, String accessToken)
            throws ApiException {

        return await()
                .atMost(config.getBatchStatusTimeout())
                .pollInterval(config.getBatchStatusInterval())
                .until(
                        () -> ksefClient.getSessionStatus(referenceNumber, accessToken),
                        response -> response.getStatus().getCode() == 200
                );
    }
}