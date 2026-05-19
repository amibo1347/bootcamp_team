package com.team.intranet.dto.ai;

import java.util.List;

/**
 * LLM 호출 요청. provider 와 무관한 추상 형식 — 각 LlmClient 구현체가 provider 별 body 로 변환.
 *
 * @param modelName    예: "gemini-1.5-flash". AiConfig 에서 채워줌.
 * @param messages     대화 히스토리. SYSTEM 1개 + USER/ASSISTANT 교대.
 * @param temperature  null 이면 provider default.
 * @param maxTokens    응답 최대 토큰. null 이면 provider default.
 */
public record LlmRequest(
    String modelName,
    List<LlmMessage> messages,
    Double temperature,
    Integer maxTokens
) {}
