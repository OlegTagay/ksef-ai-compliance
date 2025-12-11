package com.bsg6;

import com.bsg6.config.KsefConfiguration;
import com.bsg6.service.auth.AuthService;
import jakarta.xml.bind.JAXBException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
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

    EncryptionData encryptionData;

    @DataProvider
    public Object[][] getInvoicePath() {
        return new Object[][]{
                {"/xml/invoices/fa_2/invoice-min-fields.xml"}
        };
    }

    @Test(dataProvider = "getInvoicePath")
    public void sendLaptopInvoiceToKsefTest(String invoicePath) throws JAXBException, IOException, ApiException {
        String sellerNip = "1234563218";
        String accessToken = authService.authWithCustomNipAndRsa(sellerNip).accessToken();

        encryptionData = defaultCryptographyService.getEncryptionData();

        String sessionReferenceNumber = onlineSessionService.openOnlineSession(encryptionData, SystemCode.FA_2, SchemaVersion.VERSION_1_0E, SessionValue.FA, accessToken)
                .getReferenceNumber();

        // Step 2: Send invoice
        String invoiceReferenceNumber = invoiceService.sendInvoiceOnlineSession(invoicePath, sessionReferenceNumber, encryptionData, accessToken);

        onlineSessionService.waitUntilInvoicesProcessed(sessionReferenceNumber, accessToken);

        // Step 3: Close session
        onlineSessionService.closeOnlineSession(sessionReferenceNumber, accessToken);

        onlineSessionService.waitUntilUpoGenerated(sessionReferenceNumber, accessToken);

        // Step 4: Get documents
        SessionInvoiceStatusResponse sessionInvoice = onlineSessionService.getOnlineSessionDocuments(sessionReferenceNumber, accessToken);
        String ksefNumber = sessionInvoice.getKsefNumber();
        Assert.assertNotNull(ksefNumber);

        // Step 5: Get status after close
        String upoReferenceNumber = onlineSessionService.getOnlineSessionUpoAfterCloseSession(sessionReferenceNumber, accessToken).getReferenceNumber();

        // Step 6: Get UPO
        onlineSessionService.getOnlineSessionInvoiceUpo(sessionReferenceNumber, ksefNumber, accessToken);
        onlineSessionService.getOnlineSessionInvoiceUpoByInvoiceReferenceNumber(sessionReferenceNumber, invoiceReferenceNumber, accessToken);

        // Step 7: Get session UPO
        onlineSessionService.getOnlineSessionUpo(sessionReferenceNumber, upoReferenceNumber, accessToken);

        // Step 8: Get invoice
        invoiceService.getInvoice(sessionInvoice.getKsefNumber(), accessToken);
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
}
