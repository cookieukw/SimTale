package com.cookieukw.SimTale.ai;

import java.util.List;
import java.util.Map;

public record AiRequest(
        String npcName,
        String playerName,
        String systemPrompt,
        List<AiMessage> messages,
        Map<String, Object> metadata
) {}
