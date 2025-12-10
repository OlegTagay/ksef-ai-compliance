package com.bsg6.invoice;

import com.bsg6.config.ConfigurationProps;
import com.bsg6.model.AuthTokensPair;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.xml.bind.JAXBException;
import org.testng.annotations.BeforeMethod;
import org.springframework.beans.factory.annotation.Autowired;
import pl.akmf.ksef.sdk.api.DefaultKsefClient;
import pl.akmf.ksef.sdk.api.builders.auth.AuthTokenRequestBuilder;
import pl.akmf.ksef.sdk.api.builders.auth.AuthTokenRequestSerializer;
import pl.akmf.ksef.sdk.api.services.DefaultCertificateService;
import pl.akmf.ksef.sdk.api.services.DefaultQrCodeService;
import pl.akmf.ksef.sdk.api.services.DefaultSignatureService;
import pl.akmf.ksef.sdk.api.services.DefaultVerificationLinkService;
import pl.akmf.ksef.sdk.client.interfaces.*;
import pl.akmf.ksef.sdk.client.model.ApiException;
import pl.akmf.ksef.sdk.client.model.auth.*;
import pl.akmf.ksef.sdk.client.model.certificate.SelfSignedCertificate;
import pl.akmf.ksef.sdk.client.model.xml.AuthTokenRequest;
import pl.akmf.ksef.sdk.client.model.xml.SubjectIdentifierTypeEnum;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

public abstract class KsefBaseIntegrationTest {

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected CertificateService certificateService;

    @Autowired
    protected QrCodeService qrCodeService;

    @Autowired
    protected SignatureService signatureService;

    @Autowired
    protected VerificationLinkService verificationLinkService;

    @Autowired
    protected DefaultKsefClient ksefClient;

    /**
     * Initialize dependencies if Spring context is not available
     */
    @BeforeMethod
    void initializeDependencies() throws IOException {
        if (ksefClient == null) {
            ConfigurationProps apiProperties = new ConfigurationProps();

            // Initialize ObjectMapper
            if (objectMapper == null) {
                objectMapper = new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
            }

            // Initialize services
            if (certificateService == null) {
                certificateService = new DefaultCertificateService();
            }
            if (signatureService == null) {
                signatureService = new DefaultSignatureService();
            }
            if (qrCodeService == null) {
                qrCodeService = new DefaultQrCodeService();
            }
            if (verificationLinkService == null) {
                verificationLinkService = new DefaultVerificationLinkService(apiProperties.getBaseUri());
            }

            // Create HTTP client and KSeF client
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(apiProperties.getRequestTimeout())
                    .build();

            ksefClient = new DefaultKsefClient(httpClient, apiProperties, objectMapper);
        }
    }

    protected AuthTokensPair authWithCustomNip(String nip) throws ApiException, JAXBException, IOException {
        return authWithCustomNip(nip, EncryptionMethod.Rsa);
    }

    protected AuthTokensPair authWithCustomPesel(String context, String subject) throws ApiException, JAXBException, IOException {
        return authWithCustomPesel(context, subject, EncryptionMethod.Rsa);
    }

    protected AuthTokensPair authWithCustomNip(String nip, EncryptionMethod encryptionMethod) throws ApiException, JAXBException, IOException {
        AuthenticationChallengeResponse challenge = ksefClient.getAuthChallenge();

        AuthTokenRequest authTokenRequest = new AuthTokenRequestBuilder()
                .withChallenge(challenge.getChallenge())
                .withContextNip(nip)
                .withSubjectType(SubjectIdentifierTypeEnum.CERTIFICATE_SUBJECT)
                .build();

        String xml = AuthTokenRequestSerializer.authTokenRequestSerializer(authTokenRequest);

        //TODO: Can we get Company name from third party service here using NIP?
        SelfSignedCertificate cert = certificateService.getCompanySeal("Kowalski sp. z o.o", "VATPL-" + nip,
                "Kowalski", encryptionMethod);

        String signedXml = signatureService.sign(xml.getBytes(), cert.certificate(), cert.getPrivateKey());

        SignatureResponse submitAuthTokenResponse = ksefClient.submitAuthTokenRequest(signedXml, false);

        await().atMost(15, SECONDS)
                .pollInterval(1, SECONDS)
                .until(() -> isAuthProcessReady(submitAuthTokenResponse.getReferenceNumber(), submitAuthTokenResponse.getAuthenticationToken().getToken()));

        AuthOperationStatusResponse tokenResponse = ksefClient.redeemToken(submitAuthTokenResponse.getAuthenticationToken().getToken());

        return new AuthTokensPair(tokenResponse.getAccessToken().getToken(), tokenResponse.getRefreshToken().getToken());
    }

    protected AuthTokensPair authWithCustomNip(AuthTokenRequestBuilder authTokenRequestBuilder, SelfSignedCertificate cert) throws ApiException, JAXBException, IOException {
        AuthenticationChallengeResponse challenge = ksefClient.getAuthChallenge();

        AuthTokenRequest authTokenRequest = authTokenRequestBuilder
                .withChallenge(challenge.getChallenge())
                .build();

        String xml = AuthTokenRequestSerializer.authTokenRequestSerializer(authTokenRequest);

        String signedXml = signatureService.sign(xml.getBytes(), cert.certificate(), cert.getPrivateKey());

        SignatureResponse submitAuthTokenResponse = ksefClient.submitAuthTokenRequest(signedXml, false);

        //Czekanie na zakończenie procesu
        await().atMost(15, SECONDS)
                .pollInterval(1, SECONDS)
                .until(() -> isAuthProcessReady(submitAuthTokenResponse.getReferenceNumber(), submitAuthTokenResponse.getAuthenticationToken().getToken()));

        AuthOperationStatusResponse tokenResponse = ksefClient.redeemToken(submitAuthTokenResponse.getAuthenticationToken().getToken());

        return new AuthTokensPair(tokenResponse.getAccessToken().getToken(), tokenResponse.getRefreshToken().getToken());
    }

    protected AuthTokensPair authWithCustomPesel(String context, String pesel, EncryptionMethod encryptionMethod) throws ApiException, JAXBException, IOException {
        AuthenticationChallengeResponse challenge = ksefClient.getAuthChallenge();

        AuthTokenRequest authTokenRequest = new AuthTokenRequestBuilder()
                .withChallenge(challenge.getChallenge())
                .withContextNip(context)
                .withSubjectType(SubjectIdentifierTypeEnum.CERTIFICATE_SUBJECT)
                .build();

        String xml = AuthTokenRequestSerializer.authTokenRequestSerializer(authTokenRequest);

        SelfSignedCertificate cert = certificateService.getPersonalCertificate("M", "B", "PNOPL", pesel, "M B", encryptionMethod);

        String signedXml = signatureService.sign(xml.getBytes(), cert.certificate(), cert.getPrivateKey());

        SignatureResponse submitAuthTokenResponse = ksefClient.submitAuthTokenRequest(signedXml, false);

        await().atMost(14, SECONDS)
                .pollInterval(1, SECONDS)
                .until(() -> isAuthProcessReady(submitAuthTokenResponse.getReferenceNumber(), submitAuthTokenResponse.getAuthenticationToken().getToken()));

        AuthOperationStatusResponse tokenResponse = ksefClient.redeemToken(submitAuthTokenResponse.getAuthenticationToken().getToken());

        return new AuthTokensPair(tokenResponse.getAccessToken().getToken(), tokenResponse.getRefreshToken().getToken());
    }

    protected AuthTokensPair authAsPeppolProvider(String peppolId) throws ApiException, JAXBException,
            IOException {
        AuthenticationChallengeResponse challenge = ksefClient.getAuthChallenge();

        AuthTokenRequest authTokenRequest = new AuthTokenRequestBuilder()
                .withChallenge(challenge.getChallenge())
                .withPeppolId(peppolId)
                .withSubjectType(SubjectIdentifierTypeEnum.CERTIFICATE_SUBJECT)
                .build();

        String xml = AuthTokenRequestSerializer.authTokenRequestSerializer(authTokenRequest);

        SelfSignedCertificate cert = certificateService.getCompanySeal("Kowalski sp. z o.o", peppolId, peppolId);

        String signedXml = signatureService.sign(xml.getBytes(), cert.certificate(), cert.getPrivateKey());

        SignatureResponse submitAuthTokenResponse = ksefClient.submitAuthTokenRequest(signedXml, false);

        //Czekanie na zakończenie procesu
        await().atMost(14, SECONDS)
                .pollInterval(1, SECONDS)
                .until(() -> isAuthProcessReady(submitAuthTokenResponse.getReferenceNumber(), submitAuthTokenResponse.getAuthenticationToken().getToken()));

        AuthOperationStatusResponse tokenResponse = ksefClient.redeemToken(submitAuthTokenResponse.getAuthenticationToken().getToken());

        return new AuthTokensPair(tokenResponse.getAccessToken().getToken(), tokenResponse.getRefreshToken().getToken());
    }

    protected byte[] readBytesFromPath(String path) throws IOException {
        byte[] fileBytes;
        try (InputStream is = KsefBaseIntegrationTest.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new FileNotFoundException();
            }
            fileBytes = is.readAllBytes();
        }
        return fileBytes;
    }

    private boolean isAuthProcessReady(String referenceNumber, String tempAuthToken) throws ApiException {
        AuthStatus checkAuthStatus = ksefClient.getAuthStatus(referenceNumber, tempAuthToken);
        return checkAuthStatus.getStatus().getCode() == 200;
    }
}

