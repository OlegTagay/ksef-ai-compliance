package com.bsg6;

import com.bsg6.config.ConfigurationProps;
import com.bsg6.config.KsefConfiguration;
import com.bsg6.utils.IdentifierGeneratorUtils;
import com.bsg6.model.AuthTokensPair;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.Test;
import pl.akmf.ksef.sdk.api.DefaultKsefClient;
import pl.akmf.ksef.sdk.api.builders.auth.AuthTokenRequestBuilder;
import pl.akmf.ksef.sdk.api.builders.auth.AuthTokenRequestSerializer;
import pl.akmf.ksef.sdk.api.services.DefaultCertificateService;
import pl.akmf.ksef.sdk.api.services.DefaultSignatureService;
import pl.akmf.ksef.sdk.client.interfaces.CertificateService;
import pl.akmf.ksef.sdk.client.interfaces.KSeFClient;
import pl.akmf.ksef.sdk.client.interfaces.SignatureService;
import pl.akmf.ksef.sdk.client.model.auth.*;
import pl.akmf.ksef.sdk.client.model.certificate.SelfSignedCertificate;
import pl.akmf.ksef.sdk.client.model.xml.AuthTokenRequest;
import pl.akmf.ksef.sdk.client.model.xml.SubjectIdentifierTypeEnum;

import java.net.http.HttpClient;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

@SpringBootTest(classes = KsefConfiguration.class)
public class TokenIntegrationTest extends KsefBaseIntegrationTest {

    @Autowired
    protected DefaultKsefClient ksefClient;

    @Test
    public void retrieveTokenTest() throws Exception {
        // 4. Jackson
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        String contextNip = IdentifierGeneratorUtils.generateRandomNIP();

        AuthenticationChallengeResponse challenge = ksefClient.getAuthChallenge();

        AuthTokenRequest authTokenRequest = new AuthTokenRequestBuilder()
                .withChallenge(challenge.getChallenge())
                .withContextNip(contextNip)
                .withSubjectType(SubjectIdentifierTypeEnum.CERTIFICATE_SUBJECT)
                .build();

        String xml = AuthTokenRequestSerializer.authTokenRequestSerializer(authTokenRequest);

        CertificateService certificateService = new DefaultCertificateService();

        SelfSignedCertificate cert = certificateService.getCompanySeal("Kowalski sp. z o.o", "VATPL-" + contextNip,
                "Kowalski", EncryptionMethod.ECDsa);

        SignatureService signatureService = new DefaultSignatureService();

        String signedXml = signatureService.sign(xml.getBytes(), cert.certificate(), cert.getPrivateKey());

        SignatureResponse submitAuthTokenResponse = ksefClient.submitAuthTokenRequest(signedXml, false);

        //Czekanie na zakończenie procesu
        await().atMost(14, SECONDS)
                .pollInterval(1, SECONDS)
                .until(() -> {
                    AuthStatus checkAuthStatus = ksefClient.getAuthStatus(submitAuthTokenResponse.getReferenceNumber(), submitAuthTokenResponse.getAuthenticationToken().getToken());
                    return checkAuthStatus.getStatus().getCode() == 200;
                });

        AuthOperationStatusResponse tokenResponse = ksefClient.redeemToken(submitAuthTokenResponse.getAuthenticationToken().getToken());

        var authTokensPair = new AuthTokensPair(tokenResponse.getAccessToken().getToken(), tokenResponse.getRefreshToken().getToken());

        System.out.println("accessToken:" + authTokensPair.accessToken());
        System.out.println("refreshToken:" + authTokensPair.refreshToken());
    }
}
