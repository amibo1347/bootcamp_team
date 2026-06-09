package com.team.intranet.service.ai;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;

import org.springframework.stereotype.Service;

import com.team.intranet.dto.ai.LlmMessage;
import com.team.intranet.dto.ai.LlmRequest;
import com.team.intranet.dto.ai.LlmResponse;
import com.team.intranet.entity.AiConfig;
import com.team.intranet.enums.AiProvider;

import lombok.RequiredArgsConstructor;

/**
 * 현재 AiConfig 의 provider 에 맞는 LlmClient 를 선택해서 호출하는 진입점.
 * 호출자(서비스 코드)는 LlmClient 구현체를 직접 알 필요 없이 이 factory 만 사용한다.
 */
@Service
@RequiredArgsConstructor
public class LlmClientFactory {

    private final List<LlmClient> clients;
    private final AiConfigService configService;

    private Map<AiProvider, LlmClient> byProvider;

    @PostConstruct
    void init() {
        byProvider = clients.stream().collect(Collectors.toMap(LlmClient::provider, c -> c));
    }

    /** 현재 활성 provider 의 클라이언트. */
    public LlmClient currentClient() {
        AiProvider p = configService.getCurrent().getProvider();
        LlmClient c = byProvider.get(p);
        if (c == null) {
            throw new IllegalStateException("등록된 LLM 클라이언트가 없습니다: provider=" + p);
        }
        return c;
    }

    /** 현재 설정값(modelName / temperature / maxTokens)으로 LlmRequest 채워서 호출. */
    public LlmResponse generate(List<LlmMessage> messages) {
        AiConfig cfg = configService.getCurrent();
        LlmClient client = byProvider.get(cfg.getProvider());
        if (client == null) {
            throw new IllegalStateException("등록된 LLM 클라이언트가 없습니다: provider=" + cfg.getProvider());
        }
        LlmRequest req = new LlmRequest(
            cfg.getModelName(),
            messages,
            cfg.getTemperature(),
            cfg.getMaxTokens()
        );
        return client.generate(req);
    }
}
