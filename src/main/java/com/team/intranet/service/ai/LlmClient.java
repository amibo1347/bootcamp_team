package com.team.intranet.service.ai;

import com.team.intranet.dto.ai.LlmRequest;
import com.team.intranet.dto.ai.LlmResponse;
import com.team.intranet.enums.AiProvider;

/**
 * LLM 호출 추상 인터페이스. provider 별 구현체가 Spring bean 으로 등록되고
 * LlmClientFactory 가 AiConfig 의 provider 값으로 적절한 구현체를 선택한다.
 */
public interface LlmClient {

    /** 이 클라이언트가 담당하는 provider. Factory 가 provider 별 매핑에 사용. */
    AiProvider provider();

    /** 호출 가능한지 (예: API key 가 설정되어 있는지). */
    boolean isAvailable();

    /**
     * LLM 호출.
     * @throws IllegalStateException API key 미설정 등 환경 문제
     * @throws RuntimeException      provider 호출 실패 / 네트워크 오류
     */
    LlmResponse generate(LlmRequest request);
}
