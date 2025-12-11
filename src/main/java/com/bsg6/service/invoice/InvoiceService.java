package com.bsg6.service.invoice;

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
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;

@Service
public class InvoiceService {
    private DefaultKsefClient ksefClient;

    public InvoiceService(DefaultKsefClient ksefClient) {
        this.ksefClient = ksefClient;
    }

    public String sendInvoiceOnlineSession(String path, String sessionReferenceNumber, EncryptionData encryptionData,
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
