package com.bsg6.service.invoice;

import com.bsg6.model.InvoiceData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pl.akmf.ksef.sdk.api.DefaultKsefClient;
import pl.akmf.ksef.sdk.api.builders.batch.OpenBatchSessionRequestBuilder;
import pl.akmf.ksef.sdk.api.builders.session.SendInvoiceOnlineSessionRequestBuilder;
import pl.akmf.ksef.sdk.api.services.DefaultCryptographyService;
import pl.akmf.ksef.sdk.client.model.ApiException;
import pl.akmf.ksef.sdk.client.model.session.*;
import pl.akmf.ksef.sdk.client.model.session.batch.BatchPartSendingInfo;
import pl.akmf.ksef.sdk.client.model.session.batch.OpenBatchSessionRequest;
import pl.akmf.ksef.sdk.client.model.session.batch.OpenBatchSessionResponse;
import pl.akmf.ksef.sdk.client.model.session.online.SendInvoiceOnlineSessionRequest;
import pl.akmf.ksef.sdk.client.model.session.online.SendInvoiceResponse;
import pl.akmf.ksef.sdk.system.FilesUtil;
import pl.akmf.ksef.sdk.client.model.UpoVersion;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class InvoiceService {
    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

    private final DefaultKsefClient ksefClient;
    private final DefaultCryptographyService defaultCryptographyService;
    private final com.bsg6.config.ConfigurationProps config;
    private final String invoiceTemplatePath;

    public InvoiceService(DefaultKsefClient ksefClient, DefaultCryptographyService defaultCryptographyService, com.bsg6.config.ConfigurationProps config) {
        this.ksefClient = ksefClient;
        this.defaultCryptographyService = defaultCryptographyService;
        this.config = config;
        this.invoiceTemplatePath = config.getDefaultInvoiceTemplatePath();
    }

    public String sendInvoiceOnlineSession(InvoiceData invoiceTestCase, String sessionReferenceNumber, EncryptionData encryptionData,
                                           String accessToken) throws IOException, ApiException {
        // Render invoice from template with invoice number included
        String invoiceXml = renderInvoiceFromTemplate(invoiceTestCase, true);
        byte[] invoice = invoiceXml.getBytes(StandardCharsets.UTF_8);

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

    public String sendInvoiceXmlOnlineSession(String invoiceXml, String sessionReferenceNumber, EncryptionData encryptionData,
                                              String accessToken) throws ApiException {
        byte[] invoice = invoiceXml.getBytes(StandardCharsets.UTF_8);

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

    public String openBatchSessionAndSendInvoicesParts(InvoiceData invoiceTestCase, String accessToken, int invoicesCount, int partsCount) throws IOException, ApiException {
        // Render invoice from template (invoice number will be generated per-invoice by generateInvoicesInMemory)
        String invoiceTemplate = renderInvoiceFromTemplate(invoiceTestCase, false);

        EncryptionData encryptionData = defaultCryptographyService.getEncryptionData();

        Map<String, byte[]> invoicesInMemory = FilesUtil.generateInvoicesInMemory(invoicesCount, invoiceTestCase.sellerNip(), invoiceTemplate);

        for (Map.Entry<String, byte[]> stringEntry : invoicesInMemory.entrySet()) {
            log.debug("Value: {}", new String(stringEntry.getValue(), StandardCharsets.UTF_8));
        }

        byte[] zipBytes = FilesUtil.createZip(invoicesInMemory);

        // get ZIP metadata (before crypto)
        FileMetadata zipMetadata = defaultCryptographyService.getMetaData(zipBytes);

        List<byte[]> zipParts = FilesUtil.splitZip(partsCount, zipBytes);

        // Encrypt zip parts
        List<BatchPartSendingInfo> encryptedZipParts = encryptZipParts(zipParts, encryptionData.cipherKey(), encryptionData.cipherIv());

        // Build request
        OpenBatchSessionRequest request = buildOpenBatchSessionRequest(zipMetadata, encryptedZipParts, encryptionData);

        OpenBatchSessionResponse response = ksefClient.openBatchSession(request, UpoVersion.UPO_4_3, accessToken);

        if (response == null || response.getReferenceNumber() == null) {
            throw new IllegalStateException("KSeF returned no session reference number.");
        }

        ksefClient.sendBatchParts(response, encryptedZipParts);

        return response.getReferenceNumber();
    }

    private OpenBatchSessionRequest buildOpenBatchSessionRequest(FileMetadata zipMetadata, List<BatchPartSendingInfo> encryptedZipParts, EncryptionData encryptionData) {
        OpenBatchSessionRequestBuilder builder = OpenBatchSessionRequestBuilder.create()
                .withFormCode(SystemCode.FA_2, SchemaVersion.VERSION_1_0E, SessionValue.FA)
                .withOfflineMode(false)
                .withBatchFile(zipMetadata.getFileSize(), zipMetadata.getHashSHA());

        for (int i = 0; i < encryptedZipParts.size(); i++) {
            BatchPartSendingInfo part = encryptedZipParts.get(i);
            builder = builder.addBatchFilePart(i + 1,
                    part.getMetadata().getFileSize(), part.getMetadata().getHashSHA());
        }

        return builder.endBatchFile()
                .withEncryption(
                        encryptionData.encryptionInfo().getEncryptedSymmetricKey(),
                        encryptionData.encryptionInfo().getInitializationVector()
                )
                .build();
    }

    public byte[] getInvoice(String ksefNumber, String accessToken) throws ApiException {
        byte[] bytes = ksefClient.getInvoice(ksefNumber, accessToken);

//        System.out.println(new String(bytes, StandardCharsets.UTF_8));

        return bytes;
    }

    public List<SessionInvoiceStatusResponse> getInvoices(String sessionReferenceNumber, String accessToken) throws ApiException {
        SessionInvoicesResponse response = ksefClient.getSessionInvoices(sessionReferenceNumber, null, 100,
                accessToken);

        return response.getInvoices();
    }

    /**
     * Encrypts ZIP parts for batch processing.
     * Uses the injected defaultCryptographyService to avoid creating unnecessary instances.
     */
    private List<BatchPartSendingInfo> encryptZipParts(List<byte[]> zipParts, byte[] cipherKey, byte[] cipherIv) {
        List<BatchPartSendingInfo> encryptedZipParts = new ArrayList<>();
        for (int i = 0; i < zipParts.size(); i++) {
            byte[] encryptedZipPart = this.defaultCryptographyService.encryptBytesWithAES256(
                    zipParts.get(i),
                    cipherKey,
                    cipherIv
            );
            FileMetadata zipPartMetadata = this.defaultCryptographyService.getMetaData(encryptedZipPart);
            encryptedZipParts.add(new BatchPartSendingInfo(encryptedZipPart, zipPartMetadata, (i + 1)));
        }
        return encryptedZipParts;
    }

    /**
     * Renders an invoice from the template by replacing placeholders with actual data.
     *
     * @param invoiceData The invoice data to populate the template with
     * @param includeInvoiceNumber Whether to replace the invoice_number placeholder (not used for batch)
     * @return The rendered invoice as a string
     */
    private String renderInvoiceFromTemplate(InvoiceData invoiceData, boolean includeInvoiceNumber) throws IOException {
        String invoicingDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String invoiceNumber = UUID.randomUUID().toString();

        String template = new String(readBytesFromPath(invoiceTemplatePath), StandardCharsets.UTF_8);

        String rendered = template
                .replace("#seller_nip#", invoiceData.sellerNip())
                .replace("#buyer_nip#", invoiceData.buyerNip())
                .replace("#invoicing_date#", invoicingDate)
                .replace("#net#", invoiceData.netAmount().toString())
                .replace("#vat#", invoiceData.vatAmount().toString())
                .replace("#gross#", invoiceData.grossAmount().toString());

        // Invoice number replacement is handled by generateInvoicesInMemory() for batch processing
        if (includeInvoiceNumber) {
            rendered = rendered.replace("#invoice_number#", invoiceNumber);
        }

        return rendered;
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
