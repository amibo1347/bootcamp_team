package com.team.intranet.dto.ai;

/**
 * LLM 호출 응답.
 *
 * @param content           모델 텍스트 응답
 * @param promptTokens      입력 토큰 (provider 가 제공 안 하면 null)
 * @param completionTokens  출력 토큰 (provider 가 제공 안 하면 null)
 * @param model             실제 사용된 모델명
 * @param finishReason      STOP / MAX_TOKENS / SAFETY 등 (provider 별 상이)
 */
public record LlmResponse(
    String content,
    Integer promptTokens,
    Integer completionTokens,
    String model,
    String finishReason
) {}
