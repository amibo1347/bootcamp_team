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
 * Anthropic Claude REST API 호출.
 *  - 추천 모델: claude-sonnet-4-6 (최신 Sonnet, 균형) / claude-haiku-4-5-20251001 (저렴+빠름) / claude-opus-4-7 (최고 품질)
 *  - API key: 환경변수 ANTHROPIC_API_KEY (application.properties 의 ai.anthropic.api-key 로 노출).
 *  - 키 발급: https://console.anthropic.com/  (결제 등록 필수 — 가입 시 무료 크레딧 $5 정도)
 *
 *  Body:
 *  {
 *    "model": "claude-sonnet-4-6",
 *    "max_tokens": 2048,
 *    "temperature": 0.7,
 *    "system": "...",
 *    "messages": [ { "role": "user|assistant", "content": "..." }, ... ]
 *  }
 *
 *  Response:
 *  {
 *    "content": [ { "type": "text", "text": "..." } ],
 *    "stop_reason": "end_turn",
 *    "usage": { "input_tokens": 10, "output_tokens": 50 }
 *  }
 */
@Slf4j
@Component
public class AnthropicClient implements LlmClient {

    private static final String BASE_URL = "https://api.anthropic.com/v1";
    private static final String API_VERSION = "2023-06-01";
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_REF =
        new ParameterizedTypeReference<>() {};

    private final RestClient rest;
    private final String apiKey;

    public AnthropicClient(@Value("${ai.anthropic.api-key:}") String apiKey) {
        this.apiKey = apiKey;
        this.rest = RestClient.builder().baseUrl(BASE_URL).build();
    }

    @Override
    public AiProvider provider() { return AiProvider.ANTHROPIC; }

    @Override
    public boolean isAvailable() { return apiKey != null && !apiKey.isBlank(); }

    @Override
    @SuppressWarnings("unchecked")
    public LlmResponse generate(LlmRequest request) {
        if (!isAvailable()) {
            throw new IllegalStateException(
                "Anthropic API key not configured. 환경변수 ANTHROPIC_API_KEY 또는 application.properties 의 ai.anthropic.api-key 를 설정하세요.");
        }
        Map<String, Object> body = buildBody(request);
        log.info("[LLM-ANTHROPIC] generate model={} keyPrefix={} promptMessages={}",
            request.modelName(),
            apiKey.length() >= 12 ? apiKey.substring(0, 12) + "..." : "(short)",
            request.messages() != null ? request.messages().size() : 0);

        Map<String, Object> resp = null;
        RestClientResponseException lastError = null;
        long[] backoffMs = { 0, 200, 800 };
        for (int attempt = 0; attempt < backoffMs.length; attempt++) {
            if (backoffMs[attempt] > 0) {
                try { Thread.sleep(backoffMs[attempt]); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
            try {
                resp = rest.post()
                    .uri("/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", API_VERSION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(MAP_REF);
                break;
            } catch (RestClientResponseException e) {
                lastError = e;
                int status = e.getStatusCode().value();
                boolean retriable = (status == 503 || status == 529 || status == 429);
                log.warn("[LLM-ANTHROPIC] HTTP {} (attempt {}/{}) retriable={} body={}",
                    status, attempt + 1, backoffMs.length, retriable, e.getResponseBodyAsString());
                if (!retriable) break;
            }
        }
        if (resp == null) {
            int code = lastError != null ? lastError.getStatusCode().value() : -1;
            throw new RuntimeException("Anthropic 호출 실패 (HTTP " + code + ")", lastError);
        }

        // content[0].text 추출
        String content = "";
        String stopReason = null;
        List<Map<String, Object>> contents = (List<Map<String, Object>>) resp.get("content");
        if (contents != null) {
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> part : contents) {
                Object type = part.get("type");
                if ("text".equals(type)) {
                    Object t = part.get("text");
                    if (t != null) sb.append(t);
                }
            }
            content = sb.toString();
        }
        Object sr = resp.get("stop_reason");
        if (sr != null) stopReason = sr.toString();

        Integer inputTokens = null, outputTokens = null;
        Map<String, Object> usage = (Map<String, Object>) resp.get("usage");
        if (usage != null) {
            inputTokens = intOrNull(usage.get("input_tokens"));
            outputTokens = intOrNull(usage.get("output_tokens"));
        }
        return new LlmResponse(content, inputTokens, outputTokens, request.modelName(), stopReason);
    }

    /** LlmRequest → Anthropic body 변환. SYSTEM 메시지는 system 필드로 분리. */
    private Map<String, Object> buildBody(LlmRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", request.modelName());
        body.put("max_tokens", request.maxTokens() != null ? request.maxTokens() : 2048);
        if (request.temperature() != null) body.put("temperature", request.temperature());

        // 1. system
        String systemText = request.messages().stream()
            .filter(m -> m.role() == AiChatRole.SYSTEM)
            .map(LlmMessage::content)
            .reduce((a, b) -> a + "\n" + b)
            .orElse(null);
        if (systemText != null && !systemText.isBlank()) {
            body.put("system", systemText);
        }

        // 2. messages — USER/ASSISTANT 교대. Anthropic 은 첫 메시지가 반드시 user.
        List<Map<String, Object>> messages = new ArrayList<>();
        for (LlmMessage m : request.messages()) {
            if (m.role() == AiChatRole.SYSTEM) continue;
            String role = (m.role() == AiChatRole.ASSISTANT) ? "assistant" : "user";
            messages.add(Map.of(
                "role", role,
                "content", m.content() == null ? "" : m.content()
            ));
        }
        body.put("messages", messages);
        return body;
    }

    private static Integer intOrNull(Object o) {
        if (o instanceof Number n) return n.intValue();
        return null;
    }
}
