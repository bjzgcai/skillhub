package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.adminexchange.AdminExchangeRequest;
import com.iflytek.skillhub.dto.adminexchange.AdminExchangeResponse;
import com.iflytek.skillhub.service.AdminExchangeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trusted admin exchange endpoint for issuing agent-scoped SkillHub tokens.
 */
@RestController
@RequestMapping("/api/v1/auth/admin")
public class AdminExchangeController extends BaseApiController {
    private final AdminExchangeService adminExchangeService;

    public AdminExchangeController(ApiResponseFactory responseFactory,
                                   AdminExchangeService adminExchangeService) {
        super(responseFactory);
        this.adminExchangeService = adminExchangeService;
    }

    @PostMapping("/exchange")
    public ApiResponse<AdminExchangeResponse> exchange(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody AdminExchangeRequest request,
            HttpServletRequest httpRequest) {
        return ok("response.success.created", adminExchangeService.exchange(authorization, request, httpRequest));
    }
}
