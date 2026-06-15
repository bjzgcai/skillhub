package com.iflytek.skillhub.dto.adminexchange;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AdminExchangeRequest(
        @NotBlank(message = "{validation.adminExchange.source.notBlank}")
        String source,
        @NotBlank(message = "{validation.adminExchange.agentId.notBlank}")
        @Size(max = 128, message = "{validation.adminExchange.agentId.size}")
        String agentId,
        @Size(max = 128, message = "{validation.adminExchange.agentName.size}")
        String agentName,
        @NotNull(message = "{validation.adminExchange.owner.notNull}")
        @Valid
        AdminExchangeOwnerRequest owner,
        List<String> scopes,
        String expiresAt,
        String requestId
) {}
