package com.team.intranet.enums;

/**
 * AI 대화 메시지의 화자 역할.
 *  - USER:      사용자가 보낸 메시지
 *  - ASSISTANT: 모델 응답
 *  - SYSTEM:    (옵션) 시스템 지시 — 보통 메시지로 저장 X, 매 호출마다 동적으로 주입
 */
public enum AiChatRole {
    USER, ASSISTANT, SYSTEM
}
