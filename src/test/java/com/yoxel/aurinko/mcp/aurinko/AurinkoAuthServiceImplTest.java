package com.yoxel.aurinko.mcp.aurinko;

import com.yoxel.aurinko.AurinkoService;
import com.yoxel.aurinko.mcp.support.FakeAurinkoServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AurinkoAuthServiceImplTest {

    @Test
    void exchangeUsesPostAndAppAuthentication() throws Exception {
        try (var fake = new FakeAurinkoServer();
                var client = AurinkoService.createWithAppAuth(fake.getUrl(), "app-client", "app-secret")) {
            AurinkoAuthService service = new AurinkoAuthServiceImpl(client);

            var login = service.exchangeCode("provider-code");

            assertThat(login).isEqualTo(new AurinkoLogin("aurinko-user", "user-session", 42));
            assertThat(fake.getMethod()).isEqualTo("POST");
            assertThat(fake.getPath()).isEqualTo("/v1/auth/token/provider-code");
            assertThat(fake.getAuthorization()).isEqualTo("Basic " + Base64.getEncoder().encodeToString(
                    "app-client:app-secret".getBytes(StandardCharsets.UTF_8)));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 500})
    void upstreamErrorTranslated(int status) throws Exception {
        try (var fake = new FakeAurinkoServer();
                var client = AurinkoService.createWithAppAuth(fake.getUrl(), "app-client", "app-secret")) {
            fake.respond(status, "{\"code\":\"error\",\"message\":\"Failed\"}");
            AurinkoAuthService service = new AurinkoAuthServiceImpl(client);

            assertThatThrownBy(() -> service.exchangeCode("provider-code")).isInstanceOf(AurinkoException.class);
        }
    }

    @Test
    void unavailableAurinkoTranslated() throws Exception {
        String url;
        try (var fake = new FakeAurinkoServer()) {
            url = fake.getUrl();
        }
        try (var client = AurinkoService.createWithAppAuth(url, "app-client", "app-secret")) {
            AurinkoAuthService service = new AurinkoAuthServiceImpl(client);

            assertThatThrownBy(() -> service.exchangeCode("provider-code")).isInstanceOf(AurinkoException.class);
        }
    }

    @Test
    void missingUserIdRejected() throws Exception {
        try (var fake = new FakeAurinkoServer();
                var client = AurinkoService.createWithAppAuth(fake.getUrl(), "app-client", "app-secret")) {
            fake.respond(200, "{\"accountId\":42,\"userSession\":\"user-session\"}");
            AurinkoAuthService service = new AurinkoAuthServiceImpl(client);

            assertThatThrownBy(() -> service.exchangeCode("provider-code")).isInstanceOf(AurinkoException.class);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"userId\":\"aurinko-user\"}",
            "{\"userId\":\"aurinko-user\",\"userSession\":\"\"}",
            "{\"userId\":\"aurinko-user\",\"userSession\":\"   \"}"})
    void missingOrBlankUserSessionRejected(String body) throws Exception {
        try (var fake = new FakeAurinkoServer();
                var client = AurinkoService.createWithAppAuth(fake.getUrl(), "app-client", "app-secret")) {
            fake.respond(200, body);
            AurinkoAuthService service = new AurinkoAuthServiceImpl(client);

            assertThatThrownBy(() -> service.exchangeCode("provider-code")).isInstanceOf(AurinkoException.class);
        }
    }
}
