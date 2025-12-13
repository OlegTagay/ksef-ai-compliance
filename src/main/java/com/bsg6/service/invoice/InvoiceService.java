package com.bsg6.service.invoice;

import com.bsg6.model.InvoiceData;
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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class InvoiceService {
    private static final String invoiceTemplatePath = "/xml/invoices/fa_2/invoice-template-min-fields.xml";
    private DefaultKsefClient ksefClient;
    private DefaultCryptographyService defaultCryptographyService;

    public InvoiceService(DefaultKsefClient ksefClient, DefaultCryptographyService defaultCryptographyService) {
        this.ksefClient = ksefClient;
        this.defaultCryptographyService = defaultCryptographyService;
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

        EncryptionData encryptionData = defaultCryptographyService.getEncryptionData();

        Map<String, byte[]> invoicesInMemory = FilesUtil.generateInvoicesInMemory(invoicesCount, invoiceTestCase.sellerNip(), invoiceTemplate);

        byte[] zipBytes = FilesUtil.createZip(invoicesInMemory);

        // get ZIP metadata (before crypto)
        FileMetadata zipMetadata = defaultCryptographyService.getMetaData(zipBytes);

        List<byte[]> zipParts = FilesUtil.splitZip(partsCount, zipBytes);

        // Encrypt zip parts
        List<BatchPartSendingInfo> encryptedZipParts = encryptZipParts(zipParts, encryptionData.cipherKey(), encryptionData.cipherIv());

        // Build request
        OpenBatchSessionRequest request = buildOpenBatchSessionRequest(zipMetadata, encryptedZipParts, encryptionData);

        OpenBatchSessionResponse response = ksefClient.openBatchSession(request, accessToken);

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
            builder = builder.addBatchFilePart(i + 1, "faktura_part" + (i + 1) + ".zip.aes",
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

    private List<BatchPartSendingInfo> encryptZipParts(List<byte[]> zipParts, byte[] cipherKey, byte[] cipherIv) {
        DefaultCryptographyService defaultCryptographyService = new DefaultCryptographyService(ksefClient);

        List<BatchPartSendingInfo> encryptedZipParts = new ArrayList<>();
        for (int i = 0; i < zipParts.size(); i++) {
            byte[] encryptedZipPart = defaultCryptographyService.encryptBytesWithAES256(
                    zipParts.get(i),
                    cipherKey,
                    cipherIv
            );
            FileMetadata zipPartMetadata = defaultCryptographyService.getMetaData(encryptedZipPart);
            encryptedZipParts.add(new BatchPartSendingInfo(encryptedZipPart, zipPartMetadata, (i + 1)));
        }
        return encryptedZipParts;
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
