package com.bsg6;

import com.bsg6.model.InvoiceData;
import com.bsg6.model.Result;
import jakarta.xml.bind.JAXBException;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import pl.akmf.ksef.sdk.client.model.ApiException;
import pl.akmf.ksef.sdk.client.model.session.*;

import java.io.IOException;
import java.math.BigDecimal;

import static org.awaitility.Awaitility.await;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertTrue;

public class InvoiceIntegrationTest extends KsefBaseIntegrationTest {

    EncryptionData encryptionData;

    @DataProvider
    public Object[][] getTestData() {
        return new Object[][]{
                {new InvoiceData("1234563218", "8567346215", new BigDecimal(100.00), new BigDecimal(23.00), new BigDecimal(123.00)),
                        new Result(true, "valid TC")},
                {new InvoiceData("1234563218", "8567346215", new BigDecimal(100.00), new BigDecimal(23.00), new BigDecimal(123.00)),
                        new Result(false, "invalid net amount")}
        };
    }

    @Test(dataProvider = "getTestData")
    public void sendSimpleInvoiceTest(InvoiceData invoiceTestCase, Result result) throws JAXBException, IOException, ApiException {
        String accessToken = authService.authWithCustomNipAndRsa(invoiceTestCase.sellerNip()).accessToken();

        encryptionData = defaultCryptographyService.getEncryptionData();

        String sessionReferenceNumber = onlineSessionService.openOnlineSession(encryptionData, SystemCode.FA_2, SchemaVersion.VERSION_1_0E, SessionValue.FA, accessToken)
                .getReferenceNumber();

        // Step 2: Send invoice
        String invoiceReferenceNumber = invoiceService.sendInvoiceOnlineSession(invoiceTestCase, sessionReferenceNumber, encryptionData, accessToken);

        boolean isProcessed = onlineSessionService.waitUntilInvoicesProcessed(sessionReferenceNumber, accessToken);

        Assert.assertEquals(isProcessed, result.shouldPass(), result.description());

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
//        String invoicePath = "/xml/invoices/fa_2/invoice-template-min-fields.xml";
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
