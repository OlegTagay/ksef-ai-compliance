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
        return new Object[][] {
                // Valid test cases
                {new Result(true, "valid TC - basic"),
                        new InvoiceData("1234563218", "8567346215", new BigDecimal("100.00"), new BigDecimal("23.00"), new BigDecimal("123.00"))},
                {new Result(true, "valid TC - different VAT rate (8%)"),
                        new InvoiceData("1234563218", "8567346215", new BigDecimal("100.00"), new BigDecimal("8.00"), new BigDecimal("108.00"))}
//                {new Result(true, "valid TC - zero VAT"),
//                        new InvoiceData("1234563218", "8567346215", new BigDecimal("100.00"), new BigDecimal("0.00"), new BigDecimal("100.00"))},
//                {new Result(true, "valid TC - small amounts"),
//                        new InvoiceData("1234563218", "8567346215", new BigDecimal("0.01"), new BigDecimal("0.01"), new BigDecimal("0.02"))},
//                {new Result(true, "valid TC - large amounts"),
//                        new InvoiceData("1234563218", "8567346215", new BigDecimal("999999.99"), new BigDecimal("229999.99"), new BigDecimal("1229999.98"))},
//
//                // Invalid net amount
//                {new Result(false, "invalid net amount - zero"),
//                        new InvoiceData("1234563218", "8567346215", new BigDecimal("0"), new BigDecimal("23.00"), new BigDecimal("123.00"))},
//                {new Result(false, "invalid net amount - negative"),
//                        new InvoiceData("1234563218", "8567346215", new BigDecimal("-100.00"), new BigDecimal("23.00"), new BigDecimal("123.00"))},
//
//                // Invalid VAT amount
//                {new Result(false, "invalid VAT amount - negative"),
//                        new InvoiceData("1234563218", "8567346215", new BigDecimal("100.00"), new BigDecimal("-23.00"), new BigDecimal("77.00"))},
//
//                // Invalid gross amount
//                {new Result(false, "invalid gross amount - zero"),
//                        new InvoiceData("1234563218", "8567346215", new BigDecimal("100.00"), new BigDecimal("23.00"), new BigDecimal("0.00"))},
//                {new Result(false, "invalid gross amount - negative"),
//                        new InvoiceData("1234563218", "8567346215", new BigDecimal("100.00"), new BigDecimal("23.00"), new BigDecimal("-123.00"))},
//
//                // Incorrect calculations (net + vat != gross)
//                {new Result(false, "incorrect calculation - gross too high"),
//                        new InvoiceData("1234563218", "8567346215", new BigDecimal("100.00"), new BigDecimal("23.00"), new BigDecimal("150.00"))},
//                {new Result(false, "incorrect calculation - gross too low"),
//                        new InvoiceData("1234563218", "8567346215", new BigDecimal("100.00"), new BigDecimal("23.00"), new BigDecimal("100.00"))},
//                {new Result(false, "incorrect calculation - VAT mismatch"),
//                        new InvoiceData("1234563218", "8567346215", new BigDecimal("100.00"), new BigDecimal("50.00"), new BigDecimal("123.00"))},
//
//                // Invalid seller NIP scenarios
//                {new Result(false, "invalid seller NIP - too short"),
//                        new InvoiceData("123", "8567346215", new BigDecimal("100.00"), new BigDecimal("23.00"), new BigDecimal("123.00"))},
//                {new Result(false, "invalid seller NIP - single digit"),
//                        new InvoiceData("1", "8567346215", new BigDecimal("100.00"), new BigDecimal("23.00"), new BigDecimal("123.00"))},
//                {new Result(false, "invalid seller NIP - empty"),
//                        new InvoiceData("", "8567346215", new BigDecimal("100.00"), new BigDecimal("23.00"), new BigDecimal("123.00"))},
//                {new Result(false, "invalid seller NIP - too long"),
//                        new InvoiceData("12345632181234", "8567346215", new BigDecimal("100.00"), new BigDecimal("23.00"), new BigDecimal("123.00"))},
//
//                // Invalid buyer NIP scenarios
//                {new Result(false, "invalid buyer NIP - too short"),
//                        new InvoiceData("1234563218", "123", new BigDecimal("100.00"), new BigDecimal("23.00"), new BigDecimal("123.00"))},
//                {new Result(false, "invalid buyer NIP - single digit"),
//                        new InvoiceData("1234563218", "1", new BigDecimal("100.00"), new BigDecimal("23.00"), new BigDecimal("123.00"))},
//                {new Result(false, "invalid buyer NIP - empty"),
//                        new InvoiceData("1234563218", "", new BigDecimal("100.00"), new BigDecimal("23.00"), new BigDecimal("123.00"))},
//
//                // Edge cases with decimal precision
//                {new Result(true, "valid TC - high precision amounts"),
//                        new InvoiceData("1234563218", "8567346215", new BigDecimal("100.999"), new BigDecimal("23.230"), new BigDecimal("124.229"))},
//                {new Result(false, "invalid - precision mismatch in calculation"),
//                        new InvoiceData("1234563218", "8567346215", new BigDecimal("100.99"), new BigDecimal("23.23"), new BigDecimal("124.21"))},
//
//                // Boundary value analysis
//                {new Result(true, "valid TC - minimum positive values"),
//                        new InvoiceData("1234563218", "8567346215", new BigDecimal("0.01"), new BigDecimal("0.00"), new BigDecimal("0.01"))},
//                {new Result(false, "invalid - all zero amounts"),
//                        new InvoiceData("1234563218", "8567346215", new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.00"))},
//
//                // Different valid NIP combinations
//                {new Result(true, "valid TC - different NIPs"),
//                        new InvoiceData("9876543210", "1234567890", new BigDecimal("200.00"), new BigDecimal("46.00"), new BigDecimal("246.00"))},
//                {new Result(true, "valid TC - same seller and buyer NIP"),
//                        new InvoiceData("1234563218", "1234563218", new BigDecimal("100.00"), new BigDecimal("23.00"), new BigDecimal("123.00"))},
//
//                // Special VAT scenarios
//                {new Result(true, "valid TC - 5% VAT rate"),
//                        new InvoiceData("1234563218", "8567346215", new BigDecimal("1000.00"), new BigDecimal("50.00"), new BigDecimal("1050.00"))},
//                {new Result(true, "valid TC - fractional amounts"),
//                        new InvoiceData("1234563218", "8567346215", new BigDecimal("33.33"), new BigDecimal("7.66"), new BigDecimal("40.99"))}
        };
    }

    @Test(dataProvider = "getTestData")
    public void sendSimpleInvoiceTest(Result result, InvoiceData invoiceTestCase) throws Exception {
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

    @DataProvider
    public Object[][] getGeneratedXmlInvoices() throws IOException {
        java.nio.file.Path xmlDir = java.nio.file.Paths.get("src/test/resources/invoice/output/ksef/fa_2/generated");

        if (!java.nio.file.Files.exists(xmlDir)) {
            return new Object[0][0];
        }

        return java.nio.file.Files.list(xmlDir)
                .filter(path -> path.toString().endsWith(".xml"))
                .sorted()
                .map(path -> {
                    try {
                        String xmlContent = java.nio.file.Files.readString(path);
                        return new Object[]{xmlContent};
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toArray(Object[][]::new);
    }

    @Test(dataProvider = "getGeneratedXmlInvoices")
    public void sendGeneratedXmlInvoiceTest(String invoiceXml) throws Exception {
        String accessToken = authService.authWithCustomNipAndRsa("1234563218").accessToken();

        encryptionData = defaultCryptographyService.getEncryptionData();

        String sessionReferenceNumber = onlineSessionService.openOnlineSession(encryptionData, SystemCode.FA_2, SchemaVersion.VERSION_1_0E, SessionValue.FA, accessToken)
                .getReferenceNumber();

        // Step 2: Send invoice
        String invoiceReferenceNumber = invoiceService.sendInvoiceXmlOnlineSession(invoiceXml, sessionReferenceNumber, encryptionData, accessToken);

        boolean isProcessed = onlineSessionService.waitUntilInvoicesProcessed(sessionReferenceNumber, accessToken);

        Assert.assertEquals(isProcessed, true, "Should pass. File: " + invoiceXml);

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
}
