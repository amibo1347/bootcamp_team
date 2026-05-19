package com.team.intranet.service.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.team.intranet.dto.ai.LlmMessage;
import com.team.intranet.dto.ai.LlmRequest;
import com.team.intranet.dto.ai.LlmResponse;
import com.team.intranet.enums.AiChatRole;
import com.team.intranet.enums.AiProvider;

import lombok.extern.slf4j.Slf4j;

/**
 * Google Gemini REST API 호출.
 *  - 무료 tier: gemini-1.5-flash / 2.0-flash 등. 분당 15 req / 일 1,500.
 *  - API key: 환경변수 GEMINI_API_KEY (application.properties 의 ai.gemini.api-key 로 노출).
 *  - 키 발급: https://aistudio.google.com/app/apikey
 *
 *  body 형식:
 *  {
 *    "systemInstruction": { "parts": [{ "text": "..." }] },
 *    "contents": [{ "role": "user|model", "parts": [{ "text": "..." }] }],
 *    "generationConfig": { "temperature": 0.7, "maxOutputTokens": 2048 }
 *  }
 */
@Slf4j
@Component
public class GeminiClient implements LlmClient {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_REF =
        new ParameterizedTypeReference<>() {};

    private final RestClient rest;
    private final String apiKey;

    public GeminiClient(@Value("${ai.gemini.api-key:}") String apiKey) {
        this.apiKey = apiKey;
        this.rest = RestClient.builder().baseUrl(BASE_URL).build();
    }

    @Override
    public AiProvider provider() { return AiProvider.GEMINI; }

    @Override
    public boolean isAvailable() { return apiKey != null && !apiKey.isBlank(); }

    @Override
    @SuppressWarnings("unchecked")
    public LlmResponse generate(LlmRequest request) {
        if (!isAvailable()) {
            throw new IllegalStateException(
                "Gemini API key not configured. 환경변수 GEMINI_API_KEY 또는 application.properties 의 ai.gemini.api-key 를 설정하세요.");
        }
        String url = "/models/" + request.modelName() + ":generateContent?key=" + apiKey;
        Map<String, Object> body = buildBody(request);

        Map<String, Object> resp;
        try {
            resp = rest.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(MAP_REF);
        } catch (RestClientResponseException e) {
            log.warn("[LLM-GEMINI] HTTP {} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Gemini 호출 실패 (HTTP " + e.getStatusCode() + ")", e);
        }
        if (resp == null) throw new RuntimeException("Gemini empty response");

        // candidates[0].content.parts[0].text
        String content = "";
        String finishReason = null;
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) resp.get("candidates");
        if (candidates != null && !candidates.isEmpty()) {
            Map<String, Object> c0 = candidates.get(0);
            Object fr = c0.get("finishReason");
            if (fr != null) finishReason = fr.toString();
            Map<String, Object> contentObj = (Map<String, Object>) c0.get("content");
            if (contentObj != null) {
                List<Map<String, Object>> parts = (List<Map<String, Object>>) contentObj.get("parts");
                if (parts != null) {
                    StringBuilder sb = new StringBuilder();
                    for (Map<String, Object> p : parts) {
                        Object t = p.get("text");
                        if (t != null) sb.append(t);
                    }
                    content = sb.toString();
                }
            }
        }

        Integer promptTokens = null, completionTokens = null;
        Map<String, Object> usage = (Map<String, Object>) resp.get("usageMetadata");
        if (usage != null) {
            promptTokens = intOrNull(usage.get("promptTokenCount"));
            completionTokens = intOrNull(usage.get("candidatesTokenCount"));
        }
        return new LlmResponse(content, promptTokens, completionTokens, request.modelName(), finishReason);
    }

    /** LlmRequest → Gemini request body 변환. SYSTEM 메시지는 systemInstruction 으로 분리. */
    private Map<String, Object> buildBody(LlmRequest request) {
        Map<String, Object> body = new HashMap<>();

        // 1. systemInstruction (있으면)
        String systemText = request.messages().stream()
            .filter(m -> m.role() == AiChatRole.SYSTEM)
            .map(LlmMessage::content)
            .reduce((a, b) -> a + "\n" + b)
            .orElse(null);
        if (systemText != null && !systemText.isBlank()) {
            body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemText))));
        }

        // 2. contents (USER / ASSISTANT 교대)
        List<Map<String, Object>> contents = new ArrayList<>();
        for (LlmMessage m : request.messages()) {
            if (m.role() == AiChatRole.SYSTEM) continue;
            String role = (m.role() == AiChatRole.ASSISTANT) ? "model" : "user";
            contents.add(Map.of(
                "role", role,
                "parts", List.of(Map.of("text", m.content() == null ? "" : m.content()))
            ));
        }
        body.put("contents", contents);

        // 3. generationConfig
        Map<String, Object> genCfg = new HashMap<>();
        if (request.temperature() != null) genCfg.put("temperature", request.temperature());
        if (request.maxTokens() != null)   genCfg.put("maxOutputTokens", request.maxTokens());
        if (!genCfg.isEmpty()) body.put("generationConfig", genCfg);

        return body;
    }

    private static Integer intOrNull(Object o) {
        if (o instanceof Number n) return n.intValue();
        return null;
    }
}
