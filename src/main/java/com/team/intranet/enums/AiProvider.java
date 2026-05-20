package com.team.intranet.enums;

/**
 * AI 모델 제공자. MASTER 가 AiConfig 에서 선택.
 *  - GEMINI : Google Gemini (flash 무료 일 1,500 / pro 는 결제 등록 후 quota ↑).
 *  - GROQ   : Llama / Mixtral 호스팅 (후속). 속도 1위.
 *  - OLLAMA : 로컬 실행 (보안 ↑↑, 추후).
 */
public enum AiProvider {
    GEMINI,
    GROQ,
    OLLAMA
}
