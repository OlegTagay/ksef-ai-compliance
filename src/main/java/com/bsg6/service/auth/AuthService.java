package com.bsg6.service.auth;

import com.bsg6.model.AuthTokensPair;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.xml.bind.JAXBException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import pl.akmf.ksef.sdk.api.DefaultKsefClient;
import pl.akmf.ksef.sdk.api.builders.auth.AuthTokenRequestBuilder;
import pl.akmf.ksef.sdk.api.builders.auth.AuthTokenRequestSerializer;
import pl.akmf.ksef.sdk.client.interfaces.CertificateService;
import pl.akmf.ksef.sdk.client.interfaces.QrCodeService;
import pl.akmf.ksef.sdk.client.interfaces.SignatureService;
import pl.akmf.ksef.sdk.client.interfaces.VerificationLinkService;
import pl.akmf.ksef.sdk.client.model.ApiException;
import pl.akmf.ksef.sdk.client.model.auth.*;
import pl.akmf.ksef.sdk.client.model.certificate.SelfSignedCertificate;
import pl.akmf.ksef.sdk.client.model.xml.AuthTokenRequest;
import pl.akmf.ksef.sdk.client.model.xml.SubjectIdentifierTypeEnum;

import java.io.IOException;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

@Service
public class AuthService {
    private final CertificateService certificateService;
    private final SignatureService signatureService;
    private final DefaultKsefClient ksefClient;
    private final com.bsg6.config.ConfigurationProps config;

    public AuthService(CertificateService certificateService, SignatureService signatureService, DefaultKsefClient ksefClient, com.bsg6.config.ConfigurationProps config) {
        this.certificateService = certificateService;
        this.signatureService = signatureService;
        this.ksefClient = ksefClient;
        this.config = config;
    }

    public AuthTokensPair authWithCustomNipAndRsa(String nip) throws ApiException, JAXBException, IOException {
        return authWithCustomNip(nip, EncryptionMethod.Rsa);
    }

    public AuthTokensPair authWithCustomPeselAndRsa(String context, String subject) throws ApiException, JAXBException, IOException {
        return authWithCustomPesel(context, subject, EncryptionMethod.Rsa);
    }

    private AuthTokensPair authWithCustomNip(String nip, EncryptionMethod encryptionMethod) throws ApiException, JAXBException, IOException {
        // Get authentication challenge
        AuthenticationChallengeResponse challenge = ksefClient.getAuthChallenge();

        // Build auth token request with NIP context
        AuthTokenRequest authTokenRequest = new AuthTokenRequestBuilder()
                .withChallenge(challenge.getChallenge())
                .withContextNip(nip)
                .withSubjectType(SubjectIdentifierTypeEnum.CERTIFICATE_SUBJECT)
                .build();

        //TODO: Can we get Company name from third party service here using NIP?
        SelfSignedCertificate certificate = certificateService.getCompanySeal(
                config.getDefaultCompanyName(),
                "VATPL-" + nip,
                config.getDefaultCompanySubject(),
                encryptionMethod);

        // Use unified authentication flow
        return authenticate(authTokenRequest, certificate);
    }

    private AuthTokensPair authWithCustomPesel(String context, String pesel, EncryptionMethod encryptionMethod) throws ApiException, JAXBException, IOException {
        // Get authentication challenge
        AuthenticationChallengeResponse challenge = ksefClient.getAuthChallenge();

        // Build auth token request with context NIP
        AuthTokenRequest authTokenRequest = new AuthTokenRequestBuilder()
                .withChallenge(challenge.getChallenge())
                .withContextNip(context)
                .withSubjectType(SubjectIdentifierTypeEnum.CERTIFICATE_SUBJECT)
                .build();

        // Get personal certificate
        SelfSignedCertificate certificate = certificateService.getPersonalCertificate(
                config.getDefaultPersonGivenName(),
                config.getDefaultPersonSurname(),
                config.getDefaultPersonIdentifier(),
                pesel,
                config.getDefaultPersonGivenName() + " " + config.getDefaultPersonSurname(),
                encryptionMethod);

        // Use unified authentication flow
        return authenticate(authTokenRequest, certificate);
    }

    /**
     * Unified authentication method that handles the common authentication flow.
     * This eliminates code duplication across NIP, PESEL, and PEPPOL authentication methods.
     */
    private AuthTokensPair authenticate(AuthTokenRequest authTokenRequest, SelfSignedCertificate certificate)
            throws ApiException, JAXBException, IOException {
        // Serialize auth token request to XML
        String xml = AuthTokenRequestSerializer.authTokenRequestSerializer(authTokenRequest);

        // Sign the XML with the certificate
        String signedXml = signatureService.sign(xml.getBytes(), certificate.certificate(), certificate.getPrivateKey());

        // Submit the signed auth token request
        SignatureResponse submitAuthTokenResponse = ksefClient.submitAuthTokenRequest(signedXml, false);

        // Poll until authentication process is ready
        await().atMost(config.getAuthPollingTimeout())
                .pollInterval(config.getAuthPollingInterval())
                .until(() -> isAuthProcessReady(submitAuthTokenResponse.getReferenceNumber(),
                        submitAuthTokenResponse.getAuthenticationToken().getToken()));

        // Redeem the token to get access and refresh tokens
        AuthOperationStatusResponse tokenResponse = ksefClient.redeemToken(
                submitAuthTokenResponse.getAuthenticationToken().getToken());

        return new AuthTokensPair(tokenResponse.getAccessToken().getToken(),
                tokenResponse.getRefreshToken().getToken());
    }

    private boolean isAuthProcessReady(String referenceNumber, String tempAuthToken) throws ApiException {
        AuthStatus checkAuthStatus = ksefClient.getAuthStatus(referenceNumber, tempAuthToken);
        return checkAuthStatus.getStatus().getCode() == 200;
    }
}
