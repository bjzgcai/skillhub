package com.iflytek.skillhub.dto;

import java.util.List;

public record ReviewActionRequest(String comment, List<String> badgeTypes) {}
