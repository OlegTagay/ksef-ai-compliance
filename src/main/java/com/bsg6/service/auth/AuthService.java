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
    private CertificateService certificateService;

    private SignatureService signatureService;

    private DefaultKsefClient ksefClient;

    public AuthService(CertificateService certificateService, SignatureService signatureService, DefaultKsefClient ksefClient) {
        this.certificateService = certificateService;
        this.signatureService = signatureService;
        this.ksefClient = ksefClient;
    }

    public AuthTokensPair authWithCustomNipAndRsa(String nip) throws ApiException, JAXBException, IOException {
        return authWithCustomNip(nip, EncryptionMethod.Rsa);
    }

    public AuthTokensPair authWithCustomPeselAndRsa(String context, String subject) throws ApiException, JAXBException, IOException {
        return authWithCustomPesel(context, subject, EncryptionMethod.Rsa);
    }

    private AuthTokensPair authWithCustomNip(String nip, EncryptionMethod encryptionMethod) throws ApiException, JAXBException, IOException {
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

    private AuthTokensPair authWithCustomNip(AuthTokenRequestBuilder authTokenRequestBuilder, SelfSignedCertificate cert) throws ApiException, JAXBException, IOException {
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

    private AuthTokensPair authWithCustomPesel(String context, String pesel, EncryptionMethod encryptionMethod) throws ApiException, JAXBException, IOException {
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

    private AuthTokensPair authAsPeppolProvider(String peppolId) throws ApiException, JAXBException,
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

    private boolean isAuthProcessReady(String referenceNumber, String tempAuthToken) throws ApiException {
        AuthStatus checkAuthStatus = ksefClient.getAuthStatus(referenceNumber, tempAuthToken);
        return checkAuthStatus.getStatus().getCode() == 200;
    }
}
