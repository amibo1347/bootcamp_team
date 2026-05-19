package com.team.intranet.controller.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

import com.team.intranet.dto.ai.LlmMessage;
import com.team.intranet.dto.ai.LlmResponse;
import com.team.intranet.entity.AiConfig;
import com.team.intranet.enums.AiProvider;
import com.team.intranet.service.ai.AiConfigService;
import com.team.intranet.service.ai.LlmClient;
import com.team.intranet.service.ai.LlmClientFactory;
import com.team.intranet.session.MemberSession;

import lombok.RequiredArgsConstructor;

/**
 * MASTER 전용 AI 설정 API.
 *  - GET  /api/master/ai/config         : 현재 설정 조회
 *  - PUT  /api/master/ai/config         : 설정 변경 (provider / model / temp / tokens / rate limit)
 *  - GET  /api/master/ai/status         : 현재 provider 의 API key 등록 여부
 *  - POST /api/master/ai/test           : 현재 설정으로 LLM 호출 ping ({ "prompt": "..." })
 *
 * URL 이 /api/master/** 라 SecurityConfig 의 MASTER role 매처와 일치.
 * @PreAuthorize 는 보조 방어선.
 */
@RestController
@RequestMapping("/api/master/ai")
@RequiredArgsConstructor
public class AdminAiConfigController {

    private final AiConfigService configService;
    private final LlmClientFactory factory;

    @GetMapping("/config")
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<AiConfig> getConfig() {
        return ResponseEntity.ok(configService.getCurrent());
    }

    @PutMapping("/config")
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<AiConfig> updateConfig(
            @RequestBody UpdateConfigRequest body,
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        AiConfig updated = configService.update(
            body.provider(), body.modelName(),
            body.temperature(), body.maxTokens(), body.rateLimitPerDay(),
            ms.getMemberId()
        );
        return ResponseEntity.ok(updated);
    }

    /** 현재 provider 의 API key 설정 여부 + provider/model 요약. */
    @GetMapping("/status")
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<Map<String, Object>> status() {
        AiConfig cfg = configService.getCurrent();
        LlmClient client = factory.currentClient();
        Map<String, Object> out = new HashMap<>();
        out.put("provider", cfg.getProvider());
        out.put("modelName", cfg.getModelName());
        out.put("apiKeyConfigured", client.isAvailable());
        return ResponseEntity.ok(out);
    }

    /** 현재 설정으로 ping 테스트. body: { "prompt": "안녕" } */
    @PostMapping("/test")
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<Map<String, Object>> testCall(@RequestBody Map<String, String> body) {
        String prompt = body == null ? null : body.getOrDefault("prompt", "한 문장으로 자기소개 해줘");
        if (prompt == null || prompt.isBlank()) prompt = "한 문장으로 자기소개 해줘";

        LlmResponse resp = factory.generate(List.of(LlmMessage.user(prompt)));
        Map<String, Object> out = new HashMap<>();
        out.put("content", resp.content());
        out.put("model", resp.model());
        out.put("promptTokens", resp.promptTokens());
        out.put("completionTokens", resp.completionTokens());
        out.put("finishReason", resp.finishReason());
        return ResponseEntity.ok(out);
    }

    /** PUT body — 모든 필드 optional (null 이면 기존 값 유지). */
    public record UpdateConfigRequest(
        AiProvider provider,
        String modelName,
        Double temperature,
        Integer maxTokens,
        Integer rateLimitPerDay
    ) {}
}
