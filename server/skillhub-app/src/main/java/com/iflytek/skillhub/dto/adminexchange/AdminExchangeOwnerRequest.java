package com.iflytek.skillhub.dto.adminexchange;

import jakarta.validation.constraints.NotBlank;

public record AdminExchangeOwnerRequest(
        @NotBlank(message = "{validation.adminExchange.owner.provider.notBlank}")
        String provider,
        String corpId,
        String unionId,
        String userId,
        String openId
) {}
