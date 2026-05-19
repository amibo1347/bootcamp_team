package com.team.intranet.enums;

/**
 * AI 모델 제공자. MASTER 가 AiConfig 에서 선택.
 *  - GEMINI    : Google Gemini (무료 tier 기본). 분당 15 / 일 1,500 req.
 *  - ANTHROPIC : Claude (Sonnet/Opus/Haiku). 결제 필수, 품질 ↑.
 *  - GROQ      : Llama / Mixtral 호스팅 (후속). 속도 1위.
 *  - OLLAMA    : 로컬 실행 (보안 ↑↑, 추후).
 */
public enum AiProvider {
    GEMINI,
    ANTHROPIC,
    GROQ,
    OLLAMA
}
