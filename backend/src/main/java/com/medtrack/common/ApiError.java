package com.medtrack.common;

import java.util.Map;

public record ApiError(
    String code, String message, String requestId, Map<String, String> fieldErrors) {}
