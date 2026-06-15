package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.dto.adminexchange.AdminExchangeEffectiveUserResponse;
import com.iflytek.skillhub.dto.adminexchange.AdminExchangeResponse;
import com.iflytek.skillhub.dto.adminexchange.AdminExchangeSubjectResponse;
import com.iflytek.skillhub.exception.BadRequestException;
import com.iflytek.skillhub.exception.ForbiddenException;
import com.iflytek.skillhub.exception.UnauthorizedException;
import com.iflytek.skillhub.service.AdminExchangeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "skillhub.auth.dingtalk.enabled=true",
        "skillhub.auth.dingtalk.app-key=test-app-key",
        "skillhub.auth.dingtalk.app-secret=test-app-secret",
        "skillhub.auth.dingtalk.redirect-uri=https://skills.example.test/api/v1/auth/dingtalk/callback"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class AdminExchangeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private DeviceAuthService deviceAuthService;

    @MockBean
    private AdminExchangeService adminExchangeService;

    @Test
    void exchange_allowsAnonymousRequestWithDedicatedBearerToken() throws Exception {
        given(adminExchangeService.exchange(eq("Bearer platform-secret"), any(), any()))
                .willReturn(new AdminExchangeResponse(
                        "Bearer",
                        "sk_raw",
                        7L,
                        null,
                        "agent-platform",
                        new AdminExchangeSubjectResponse("AGENT", "agent-1", "lobster"),
                        new AdminExchangeEffectiveUserResponse("user-1", "Alice", "dingtalk", "corp-1", "union-1"),
                        List.of("skill:read", "skill:publish")
                ));

        mockMvc.perform(post("/api/v1/auth/admin/exchange")
                        .header("Authorization", "Bearer platform-secret")
                        .contentType("application/json")
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").value("sk_raw"))
                .andExpect(jsonPath("$.data.subject.type").value("AGENT"));
    }

    @Test
    void exchange_returns401WhenAuthorizationMissing() throws Exception {
        given(adminExchangeService.exchange(isNull(), any(), any()))
                .willThrow(new UnauthorizedException("error.adminExchange.tokenMissing"));

        mockMvc.perform(post("/api/v1/auth/admin/exchange")
                        .contentType("application/json")
                        .content(validRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void exchange_returns403WhenDisabled() throws Exception {
        given(adminExchangeService.exchange(eq("Bearer platform-secret"), any(), any()))
                .willThrow(new ForbiddenException("error.adminExchange.disabled"));

        mockMvc.perform(post("/api/v1/auth/admin/exchange")
                        .header("Authorization", "Bearer platform-secret")
                        .contentType("application/json")
                        .content(validRequest()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void exchange_returns400WhenOwnerIsNotBound() throws Exception {
        given(adminExchangeService.exchange(eq("Bearer platform-secret"), any(), any()))
                .willThrow(new BadRequestException("error.adminExchange.ownerNotBound"));

        mockMvc.perform(post("/api/v1/auth/admin/exchange")
                        .header("Authorization", "Bearer platform-secret")
                        .contentType("application/json")
                        .content(validRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void exchange_returns400WhenOwnerIdentifierIsMissing() throws Exception {
        given(adminExchangeService.exchange(eq("Bearer platform-secret"), any(), any()))
                .willThrow(new BadRequestException("error.adminExchange.ownerIdentifierMissing"));

        mockMvc.perform(post("/api/v1/auth/admin/exchange")
                        .header("Authorization", "Bearer platform-secret")
                        .contentType("application/json")
                        .content("""
                                {
                                  "source":"agent-platform",
                                  "agentId":"agent-1",
                                  "owner":{
                                    "provider":"dingtalk",
                                    "userId":"ding-user-1"
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    private String validRequest() {
        return """
                {
                  "source":"agent-platform",
                  "agentId":"agent-1",
                  "agentName":"lobster",
                  "owner":{
                    "provider":"dingtalk",
                    "corpId":"corp-1",
                    "unionId":"union-1",
                    "userId":"ding-user-1"
                  }
                }
                """;
    }
}
