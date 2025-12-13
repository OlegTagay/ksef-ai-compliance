package com.bsg6;

import com.bsg6.model.InvoiceData;
import com.bsg6.utils.IdentifierGeneratorUtils;
import jakarta.xml.bind.JAXBException;
import org.testng.annotations.Test;
import pl.akmf.ksef.sdk.client.model.ApiException;
import pl.akmf.ksef.sdk.client.model.session.SessionInvoiceStatusResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

public class BatchInvoiceTest extends KsefBaseIntegrationTest {
    private static final int DEFAULT_NUMBER_OF_PARTS = 2;
    private static final int DEFAULT_INVOICES_COUNT = 35;

    @Test
    void batchSessionE2EIntegrationTest() throws JAXBException, IOException, ApiException {
        String contextNip = IdentifierGeneratorUtils.generateRandomNIP();
        String accessToken = authService.authWithCustomNipAndRsa(contextNip).accessToken();

        InvoiceData invoiceTestCase = new InvoiceData("1234563218", "8567346215", new BigDecimal("100.00"), new BigDecimal("23.00"), new BigDecimal("123.00"));

        String sessionReferenceNumber = invoiceService.openBatchSessionAndSendInvoicesParts(invoiceTestCase, accessToken, DEFAULT_INVOICES_COUNT, DEFAULT_NUMBER_OF_PARTS);

        onlineSessionService.closeBatchSession(sessionReferenceNumber, accessToken);

        String upoReferenceNumber = onlineSessionService.getBatchSessionStatus(sessionReferenceNumber, accessToken)
                .getUpo().getPages().getFirst().getReferenceNumber();

        List<SessionInvoiceStatusResponse> documents = invoiceService.getInvoices(sessionReferenceNumber, accessToken);

        onlineSessionService.getOnlineSessionInvoiceUpo(sessionReferenceNumber, documents.getFirst().getKsefNumber(), accessToken);

        onlineSessionService.getOnlineSessionUpo(sessionReferenceNumber, upoReferenceNumber, accessToken);
    }
}
