package com.bsg6.service.invoice;

import com.bsg6.model.InvoiceData;
import org.springframework.stereotype.Service;
import pl.akmf.ksef.sdk.api.DefaultKsefClient;
import pl.akmf.ksef.sdk.api.builders.session.SendInvoiceOnlineSessionRequestBuilder;
import pl.akmf.ksef.sdk.api.services.DefaultCryptographyService;
import pl.akmf.ksef.sdk.client.model.ApiException;
import pl.akmf.ksef.sdk.client.model.session.EncryptionData;
import pl.akmf.ksef.sdk.client.model.session.FileMetadata;
import pl.akmf.ksef.sdk.client.model.session.online.SendInvoiceOnlineSessionRequest;
import pl.akmf.ksef.sdk.client.model.session.online.SendInvoiceResponse;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;

@Service
public class InvoiceService {
    private static final String invoiceTemplatePath = "/xml/invoices/fa_2/invoice-template-min-fields.xml";
    private DefaultKsefClient ksefClient;

    public InvoiceService(DefaultKsefClient ksefClient) {
        this.ksefClient = ksefClient;
    }

    public String sendInvoiceOnlineSession(InvoiceData invoiceTestCase, String sessionReferenceNumber, EncryptionData encryptionData,
                                           String accessToken) throws IOException, ApiException {
        String invoicingDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String invoiceNumber = UUID.randomUUID().toString();

        String invoiceTemplate = new String(readBytesFromPath(invoiceTemplatePath), StandardCharsets.UTF_8)
                .replace("{{seller_nip}}", invoiceTestCase.sellerNip())
                .replace("{{buyer_nip}}", invoiceTestCase.buyerNip())
                .replace("{{invoicing_date}}", invoicingDate)
                .replace("{{invoice_number}}", invoiceNumber)
                .replace("{{net}}", invoiceTestCase.netAmount().toString())
                .replace("{{vat}}", invoiceTestCase.vatAmount().toString())
                .replace("{{gross}}", invoiceTestCase.grossAmount().toString());

        byte[] invoice = invoiceTemplate.getBytes(StandardCharsets.UTF_8);
        DefaultCryptographyService defaultCryptographyService = new DefaultCryptographyService(ksefClient);

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

        return sendInvoiceResponse.getReferenceNumber();
    }

    public byte[] getInvoice(String ksefNumber, String accessToken) throws ApiException {

        return ksefClient.getInvoice(ksefNumber, accessToken);
    }

    private byte[] readBytesFromPath(String path) throws IOException {
        byte[] fileBytes;
        try (InputStream is = InvoiceService.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new FileNotFoundException();
            }
            fileBytes = is.readAllBytes();
        }
        return fileBytes;
    }
}
