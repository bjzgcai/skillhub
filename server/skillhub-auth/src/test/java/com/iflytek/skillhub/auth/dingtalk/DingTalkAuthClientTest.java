package com.iflytek.skillhub.auth.dingtalk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iflytek.skillhub.auth.exception.AuthFlowException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DingTalkAuthClientTest {

    @Test
    void resolveBrowserIdentityShouldSendDocumentedBrowserTokenExchangeRequestBody() {
        TestClient testClient = new TestClient();
        testClient.expectPostJson(
            "https://oapi.dingtalk.com/sns/gettoken",
            "\"clientId\":\"app-key\"",
            "\"clientSecret\":\"app-secret\"",
            "\"code\":\"auth-code-body\"",
            "\"refreshToken\":\"\"",
            "\"grantType\":\"authorization_code\""
        , "{\"accessToken\":\"browser-access-token\"}");
        testClient.expectGet(
            "https://api.dingtalk.com/v1.0/contact/users/me",
            "{\"unionId\":\"union-body\",\"nick\":\"Alice\"}"
        );
        testClient.expectPost(
            "https://api.dingtalk.com/v1.0/oauth2/accessToken",
            "{\"accessToken\":\"app-access-token\",\"expireIn\":7200}"
        );
        testClient.expectPost(
            "https://oapi.dingtalk.com/topapi/v2/user/get?access_token=app-access-token",
            "{\"result\":{\"name\":\"Alice\"}}"
        );

        DingTalkAuthClient.DingTalkUserIdentity identity = testClient.client()
            .resolveBrowserIdentity("auth-code-body");

        assertThat(identity.unionId()).isEqualTo("union-body");
    }

    @Test
    void resolveBrowserIdentityShouldAcceptUnionIdFromBrowserUserInfo() {
        TestClient testClient = new TestClient();
        testClient.expectPost(
            "https://oapi.dingtalk.com/sns/gettoken",
            "{\"accessToken\":\"browser-access-token\"}"
        );
        testClient.expectGet(
            "https://api.dingtalk.com/v1.0/contact/users/me",
            "{\"unionId\":\"union-1\",\"nick\":\"Alice\",\"email\":\"alice@example.com\"}"
        );
        testClient.expectPost(
            "https://api.dingtalk.com/v1.0/oauth2/accessToken",
            "{\"accessToken\":\"app-access-token\",\"expireIn\":7200}"
        );
        testClient.expectPost(
            "https://oapi.dingtalk.com/topapi/v2/user/get?access_token=app-access-token",
            "{\"result\":{\"name\":\"Alice\",\"email\":\"alice@example.com\"}}"
        );

        DingTalkAuthClient.DingTalkUserIdentity identity = testClient.client()
            .resolveBrowserIdentity("auth-code-1");

        assertThat(identity.unionId()).isEqualTo("union-1");
        assertThat(identity.displayName()).isEqualTo("Alice");
        assertThat(identity.email()).isEqualTo("alice@example.com");
    }

    @Test
    void resolveBrowserIdentityShouldAcceptOpenIdAliasFromBrowserUserInfo() {
        TestClient testClient = new TestClient();
        testClient.expectPost(
            "https://oapi.dingtalk.com/sns/gettoken",
            "{\"accessToken\":\"browser-access-token\"}"
        );
        testClient.expectGet(
            "https://api.dingtalk.com/v1.0/contact/users/me",
            "{\"openId\":\"open-1\",\"nick\":\"Bob\"}"
        );
        testClient.expectPost(
            "https://api.dingtalk.com/v1.0/oauth2/accessToken",
            "{\"accessToken\":\"app-access-token\",\"expireIn\":7200}"
        );
        testClient.expectPost(
            "https://oapi.dingtalk.com/topapi/v2/user/get?access_token=app-access-token",
            "{\"result\":{\"name\":\"Bob\"}}"
        );

        DingTalkAuthClient.DingTalkUserIdentity identity = testClient.client()
            .resolveBrowserIdentity("auth-code-2");

        assertThat(identity.unionId()).isEqualTo("open-1");
        assertThat(identity.displayName()).isEqualTo("Bob");
    }

    @Test
    void resolveBrowserIdentityShouldFallbackToSubClaimFromAccessToken() {
        TestClient testClient = new TestClient();
        String jwtLikeToken = jwtWithPayload("{\"sub\":\"sub-1\"}");
        testClient.expectPost(
            "https://oapi.dingtalk.com/sns/gettoken",
            "{\"accessToken\":\"" + jwtLikeToken + "\"}"
        );
        testClient.expectGet(
            "https://api.dingtalk.com/v1.0/contact/users/me",
            "{\"nick\":\"Carol\"}"
        );
        testClient.expectPost(
            "https://api.dingtalk.com/v1.0/oauth2/accessToken",
            "{\"accessToken\":\"app-access-token\",\"expireIn\":7200}"
        );
        testClient.expectPost(
            "https://oapi.dingtalk.com/topapi/v2/user/get?access_token=app-access-token",
            "{\"result\":{\"name\":\"Carol\"}}"
        );

        DingTalkAuthClient.DingTalkUserIdentity identity = testClient.client()
            .resolveBrowserIdentity("auth-code-3");

        assertThat(identity.unionId()).isEqualTo("sub-1");
        assertThat(identity.displayName()).isEqualTo("Carol");
    }

    @Test
    void resolveBrowserIdentityShouldRejectWhenNoIdentityFieldPresent() {
        TestClient testClient = new TestClient();
        testClient.expectPost(
            "https://oapi.dingtalk.com/sns/gettoken",
            "{\"accessToken\":\"opaque-token\"}"
        );
        testClient.expectGet(
            "https://api.dingtalk.com/v1.0/contact/users/me",
            "{\"nick\":\"Nobody\"}"
        );

        assertThatThrownBy(() -> testClient.client().resolveBrowserIdentity("auth-code-4"))
            .isInstanceOf(AuthFlowException.class)
            .satisfies(ex -> {
                AuthFlowException authException = (AuthFlowException) ex;
                assertThat(authException.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(authException.getMessageCode()).isEqualTo("error.auth.dingtalk.userInfoIncomplete");
            });
    }

    private static String jwtWithPayload(String payloadJson) {
        return "header."
            + Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8))
            + ".signature";
    }

    private static final class TestClient {
        private final MockRestServiceServer server;
        private final DingTalkAuthClient client;

        private TestClient() {
            RestClient.Builder builder = RestClient.builder();
            this.server = MockRestServiceServer.bindTo(builder).build();

            DingTalkAuthProperties properties = new DingTalkAuthProperties();
            properties.setEnabled(true);
            properties.setAppKey("app-key");
            properties.setAppSecret("app-secret");
            properties.setRedirectUri("https://skillhub.example.com/api/v1/auth/dingtalk/callback");
            properties.setBrowserUserTokenUrl("https://oapi.dingtalk.com/sns/gettoken");
            properties.setBrowserUserInfoUrl("https://api.dingtalk.com/v1.0/contact/users/me");
            properties.setAppTokenUrl("https://api.dingtalk.com/v1.0/oauth2/accessToken");
            properties.setUserDetailUrl("https://oapi.dingtalk.com/topapi/v2/user/get");
            properties.setRequireCorpMembership(false);

            this.client = new DingTalkAuthClient(
                properties,
                builder.build(),
                Clock.fixed(Instant.parse("2026-04-02T06:00:00Z"), ZoneOffset.UTC)
            );
        }

        DingTalkAuthClient client() {
            return client;
        }

        void expectGet(String url, String body) {
            server.expect(request -> {
                    assertThat(request.getMethod()).isEqualTo(HttpMethod.GET);
                    assertThat(request.getURI()).isEqualTo(URI.create(url));
                })
                .andRespond(request -> {
                    MockClientHttpResponse response = new MockClientHttpResponse(
                        body.getBytes(StandardCharsets.UTF_8),
                        HttpStatus.OK
                    );
                    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                    return response;
                });
        }

        void expectPost(String url, String body) {
            server.expect(request -> {
                    assertThat(request.getMethod()).isEqualTo(HttpMethod.POST);
                    assertThat(request.getURI()).isEqualTo(URI.create(url));
                })
                .andRespond(request -> {
                    MockClientHttpResponse response = new MockClientHttpResponse(
                        body.getBytes(StandardCharsets.UTF_8),
                        HttpStatus.OK
                    );
                    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                    return response;
                });
        }

        void expectPostJson(String url, String contains1, String contains2, String contains3, String contains4, String contains5, String body) {
            server.expect(request -> {
                    assertThat(request.getMethod()).isEqualTo(HttpMethod.POST);
                    assertThat(request.getURI()).isEqualTo(URI.create(url));
                    String requestBody = ((MockClientHttpRequest) request).getBodyAsString(StandardCharsets.UTF_8);
                    assertThat(requestBody)
                        .contains(contains1)
                        .contains(contains2)
                        .contains(contains3)
                        .contains(contains4)
                        .contains(contains5);
                })
                .andRespond(request -> {
                    MockClientHttpResponse response = new MockClientHttpResponse(
                        body.getBytes(StandardCharsets.UTF_8),
                        HttpStatus.OK
                    );
                    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                    return response;
                });
        }
    }
}
