package com.iflytek.skillhub.dto.adminexchange;

import java.util.List;

public record AdminExchangeResponse(
        String tokenType,
        String accessToken,
        Long tokenId,
        String expiresAt,
        String source,
        AdminExchangeSubjectResponse subject,
        AdminExchangeEffectiveUserResponse effectiveUser,
        List<String> scopes
) {}
