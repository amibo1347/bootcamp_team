package com.team.intranet.dto.ai;

import com.team.intranet.enums.AiChatRole;

/**
 * LLM 입력/출력 메시지 한 건. provider 와 무관한 추상 표현.
 * Role 은 엔티티와 같은 {@link AiChatRole} 을 그대로 재사용.
 */
public record LlmMessage(AiChatRole role, String content) {

    public static LlmMessage system(String content) { return new LlmMessage(AiChatRole.SYSTEM, content); }
    public static LlmMessage user(String content)   { return new LlmMessage(AiChatRole.USER, content); }
    public static LlmMessage assistant(String content) { return new LlmMessage(AiChatRole.ASSISTANT, content); }
}
