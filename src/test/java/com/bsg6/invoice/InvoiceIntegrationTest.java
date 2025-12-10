package com.bsg6.invoice;

import jakarta.xml.bind.JAXBException;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import pl.akmf.ksef.sdk.api.builders.session.OpenOnlineSessionRequestBuilder;
import pl.akmf.ksef.sdk.api.builders.session.SendInvoiceOnlineSessionRequestBuilder;
import pl.akmf.ksef.sdk.api.services.DefaultCryptographyService;
import pl.akmf.ksef.sdk.client.model.ApiException;
import pl.akmf.ksef.sdk.client.model.session.*;
import pl.akmf.ksef.sdk.client.model.session.online.OpenOnlineSessionRequest;
import pl.akmf.ksef.sdk.client.model.session.online.OpenOnlineSessionResponse;
import pl.akmf.ksef.sdk.client.model.session.online.SendInvoiceOnlineSessionRequest;
import pl.akmf.ksef.sdk.client.model.session.online.SendInvoiceResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertTrue;

public class InvoiceIntegrationTest extends KsefBaseIntegrationTest {
    @Autowired(required = false)
    private DefaultCryptographyService defaultCryptographyService;

    private EncryptionData encryptionData;

    @BeforeMethod
    void initializeCryptographyService() {
        if (defaultCryptographyService == null && ksefClient != null) {
            defaultCryptographyService = new DefaultCryptographyService(ksefClient);
        }
    }

    @DataProvider()
    public Object[][] getInvoicePath() {
        return new Object[][]{
                {"/xml/invoices/fa_2/invoice-min-fields.xml"}
//                {"/xml/invoices/fa_2/invoice-template.xml"}
//                {"/xml/invoices/fa_2/invoice-template-laptop.xml"}
//                {"/xml/invoices/fa_2/invoice-template-laptop2.xml"},
//                {"/xml/invoices/fa_2/invoice-min-fields.xml"}
        };
    }

    @Test(dataProvider = "getInvoicePath")
    public void sendLaptopInvoiceToKsefTest(String invoicePath) throws JAXBException, IOException, ApiException {
        String sellerNip = "1234563218";
        String accessToken = authWithCustomNip(sellerNip).accessToken();

        encryptionData = defaultCryptographyService.getEncryptionData();

        // Step 1: Open session and return referenceNumber
        String sessionReferenceNumber = openOnlineSession(encryptionData, SystemCode.FA_2, SchemaVersion.VERSION_1_0E, SessionValue.FA, accessToken);

        // Step 2: Send invoice
        String invoiceReferenceNumber = sendInvoiceOnlineSession(invoicePath, sessionReferenceNumber, encryptionData, accessToken);

        // Wait for invoice to be processed && check session status
        await().atMost(20, SECONDS)
                .pollInterval(5, SECONDS)
                .until(() -> isInvoicesInSessionProcessed(sessionReferenceNumber, accessToken));

        // Step 3: Close session
        closeOnlineSession(sessionReferenceNumber, accessToken);

        await().atMost(60, SECONDS)
                .pollInterval(5, SECONDS)
                .until(() -> isUpoGenerated(sessionReferenceNumber, accessToken));

        // Step 4: Get documents
        SessionInvoiceStatusResponse sessionInvoice = getOnlineSessionDocuments(sessionReferenceNumber, accessToken);
        String ksefNumber = sessionInvoice.getKsefNumber();
        Assert.assertNotNull(ksefNumber);

        // Step 5: Get status after close
        String upoReferenceNumber = getOnlineSessionUpoAfterCloseSession(sessionReferenceNumber, accessToken);

        // Step 6: Get UPO
        getOnlineSessionInvoiceUpo(sessionReferenceNumber, ksefNumber, accessToken);
        getOnlineSessionInvoiceUpoByInvoiceReferenceNumber(sessionReferenceNumber, invoiceReferenceNumber, accessToken);

        // Step 7: Get session UPO
        getOnlineSessionUpo(sessionReferenceNumber, upoReferenceNumber, accessToken);

        // Step 8: Get invoice
        getInvoice(sessionInvoice.getKsefNumber(), accessToken);
    }

//    @Test
//    public void sendLaptopInvoiceToKsefTest() throws Exception {
//        String sellerNip = "1234563218";
//        String buyerNip = "8567346215";
//        String invoicePath = "/xml/invoices/fa_2/invoice-min-fields.xml";
//
//        String ksefNumber = invoiceService.sendInvoice(invoicePath, sellerNip, buyerNip);
//
//        assertNotNull(ksefNumber);
//
//        String accessToken = ksefAuthService.authWithCustomNip(sellerNip, sellerNip).accessToken();
//        byte[] invoiceXml = invoiceService.getInvoice(ksefNumber, accessToken);
//        assertTrue(invoiceXml.length > 0);
//    }

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
            Assert.fail(e.getMessage());
        }
        return false;
    }

    private String getOnlineSessionUpoAfterCloseSession(String sessionReferenceNumber, String accessToken) throws ApiException {
        SessionStatusResponse statusResponse = ksefClient.getSessionStatus(sessionReferenceNumber, accessToken);
        Assert.assertNotNull(statusResponse);
        Assert.assertNotNull(statusResponse.getSuccessfulInvoiceCount());
        Assert.assertEquals((int) statusResponse.getSuccessfulInvoiceCount(), 1);
        Assert.assertNull(statusResponse.getFailedInvoiceCount());
        Assert.assertNotNull(statusResponse.getUpo());
        Assert.assertEquals((int) statusResponse.getStatus().getCode(), 200);
        UpoPageResponse upoPageResponse = statusResponse.getUpo().getPages().getFirst();
        Assert.assertNotNull(upoPageResponse);
        Assert.assertNotNull(upoPageResponse.getReferenceNumber());

        return upoPageResponse.getReferenceNumber();
    }

    private String openOnlineSession(EncryptionData encryptionData, SystemCode systemCode,
                                     SchemaVersion schemaVersion, SessionValue value, String accessToken) throws ApiException {
        OpenOnlineSessionRequest request = new OpenOnlineSessionRequestBuilder()
                .withFormCode(new FormCode(systemCode, schemaVersion, value))
                .withEncryptionInfo(encryptionData.encryptionInfo())
                .build();

        OpenOnlineSessionResponse openOnlineSessionResponse = ksefClient.openOnlineSession(request, accessToken);
        Assert.assertNotNull(openOnlineSessionResponse);
        Assert.assertNotNull(openOnlineSessionResponse.getReferenceNumber());
        return openOnlineSessionResponse.getReferenceNumber();
    }

    private String sendInvoiceOnlineSession(String path, String sessionReferenceNumber, EncryptionData encryptionData,
                                            String accessToken) throws IOException, ApiException {
        String invoicingDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        String invoiceCreationDate = Instant.now().toString();
        String invoiceNumber = UUID.randomUUID().toString();
        String uuid = UUID.randomUUID().toString();

        String invoiceTemplate = new String(readBytesFromPath(path), StandardCharsets.UTF_8)
                .replace("#invoicing_date#", invoicingDate)
                .replace("#invoice_creation_date#", invoiceCreationDate)
                .replace("#invoice_number#", invoiceNumber)
                .replace("#uuid#", uuid);

        byte[] invoice = invoiceTemplate.getBytes(StandardCharsets.UTF_8);

        byte[] encryptedInvoice = defaultCryptographyService.encryptBytesWithAES256(invoice,
                encryptionData.cipherKey(),
                encryptionData.cipherIv());

        FileMetadata invoiceMetadata = defaultCryptographyService.getMetaData(invoice);
        FileMetadata encryptedInvoiceMetadata = defaultCryptographyService.getMetaData(encryptedInvoice);

        SendInvoiceOnlineSessionRequest sendInvoiceOnlineSessionRequest = new SendInvoiceOnlineSessionRequestBuilder()
                .withInvoiceHash(invoiceMetadata.getHashSHA())
                .withInvoiceSize(invoiceMetadata.getFileSize())
                .withEncryptedInvoiceHash(encryptedInvoiceMetadata.getHashSHA())
                .withEncryptedInvoiceSize(encryptedInvoiceMetadata.getFileSize())
                .withEncryptedInvoiceContent(Base64.getEncoder().encodeToString(encryptedInvoice))
                .build();

        SendInvoiceResponse sendInvoiceResponse = ksefClient.onlineSessionSendInvoice(sessionReferenceNumber, sendInvoiceOnlineSessionRequest, accessToken);
        Assert.assertNotNull(sendInvoiceResponse);
        Assert.assertNotNull(sendInvoiceResponse.getReferenceNumber());

        return sendInvoiceResponse.getReferenceNumber();
    }

    private void closeOnlineSession(String sessionReferenceNumber, String accessToken) throws ApiException {
        ksefClient.closeOnlineSession(sessionReferenceNumber, accessToken);
    }

    private SessionInvoiceStatusResponse getOnlineSessionDocuments(String sessionReferenceNumber, String accessToken) throws ApiException {
        SessionInvoicesResponse sessionInvoices = ksefClient.getSessionInvoices(sessionReferenceNumber, null, 10, accessToken);
        Assert.assertEquals(sessionInvoices.getInvoices().size(), 1);
        SessionInvoiceStatusResponse invoice = sessionInvoices.getInvoices().getFirst();
        Assert.assertNotNull(invoice);
        Assert.assertNotNull(invoice.getOrdinalNumber());
        Assert.assertNotNull(invoice.getInvoiceNumber());
        Assert.assertNotNull(invoice.getKsefNumber());
        Assert.assertNotNull(invoice.getReferenceNumber());
        Assert.assertNotNull(invoice.getInvoiceHash());
        Assert.assertNotNull(invoice.getInvoicingDate());
        Assert.assertNotNull(invoice.getStatus());
        Assert.assertEquals((int) invoice.getStatus().getCode(), 200);

        return invoice;
    }

    private void getOnlineSessionInvoiceUpo(String sessionReferenceNumber, String ksefNumber, String accessToken) throws ApiException {
        byte[] upoResponse = ksefClient.getSessionInvoiceUpoByKsefNumber(sessionReferenceNumber, ksefNumber, accessToken);

        Assert.assertNotNull(upoResponse);
    }

    private void getOnlineSessionInvoiceUpoByInvoiceReferenceNumber(String sessionReferenceNumber, String invoiceReferenceNumber, String accessToken) throws ApiException {
        byte[] upoResponse = ksefClient.getSessionInvoiceUpoByReferenceNumber(sessionReferenceNumber, invoiceReferenceNumber, accessToken);

        Assert.assertNotNull(upoResponse);
    }

    private void getOnlineSessionUpo(String sessionReferenceNumber, String upoReferenceNumber, String accessToken) throws ApiException {
        byte[] sessionUpo = ksefClient.getSessionUpo(sessionReferenceNumber, upoReferenceNumber, accessToken);

        Assert.assertNotNull(sessionUpo);
    }

    private void getInvoice(String ksefNumber, String accessToken) throws ApiException {
        byte[] invoice = ksefClient.getInvoice(ksefNumber, accessToken);

        System.out.println(new String(invoice, StandardCharsets.UTF_8));

        Assert.assertNotNull(invoice);
    }
}
