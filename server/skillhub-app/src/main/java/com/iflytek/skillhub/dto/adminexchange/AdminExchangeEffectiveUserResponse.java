package com.iflytek.skillhub.dto.adminexchange;

public record AdminExchangeEffectiveUserResponse(
        String id,
        String displayName,
        String provider,
        String corpId,
        String unionId
) {}
