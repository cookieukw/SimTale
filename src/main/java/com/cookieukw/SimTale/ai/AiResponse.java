package com.cookieukw.SimTale.ai;

public record AiResponse(
        boolean success,
        String text,
        String rawBody,
        String errorMessage
) {
    public static AiResponse ok(String text, String rawBody) {
        return new AiResponse(true, text, rawBody, null);
    }

    public static AiResponse fail(String errorMessage, String rawBody) {
        return new AiResponse(false, null, rawBody, errorMessage);
    }
}
