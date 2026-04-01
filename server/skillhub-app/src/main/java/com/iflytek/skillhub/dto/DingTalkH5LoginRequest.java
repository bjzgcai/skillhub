package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;

public record DingTalkH5LoginRequest(
    @NotBlank(message = "钉钉免登 code 不能为空")
    String code
) {}
