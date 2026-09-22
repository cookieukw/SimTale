package com.cookieukw.SimTale.ai;

public interface NpcAiProvider {
    String id();
    AiResponse generate(AiRequest request);
}
