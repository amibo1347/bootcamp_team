package com.team.intranet.service.ai;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team.intranet.entity.AiConfig;
import com.team.intranet.enums.AiProvider;
import com.team.intranet.repository.AiConfigRepository;

import lombok.RequiredArgsConstructor;

/**
 * AI 설정 (singleton) 관리.
 *  - 첫 호출 시 default 자동 생성.
 *  - update() 는 부분 변경 (null 인 필드는 기존 값 유지).
 */
@Service
@RequiredArgsConstructor
public class AiConfigService {

    private final AiConfigRepository repo;

    @Transactional
    public AiConfig getCurrent() {
        return repo.findFirstByOrderByConfigIdAsc()
            .orElseGet(() -> repo.save(AiConfig.defaultConfig()));
    }

    /**
     * MASTER 가 부분 변경. null 인 필드는 기존 값 유지.
     * @param updatedByMemberId 감사 로그용 (null 허용)
     */
    @Transactional
    public AiConfig update(AiProvider provider, String modelName,
                           Double temperature, Integer maxTokens, Integer rateLimitPerDay,
                           Long updatedByMemberId) {
        AiConfig cfg = getCurrent();
        if (provider != null)        cfg.setProvider(provider);
        if (modelName != null && !modelName.isBlank()) cfg.setModelName(modelName);
        if (temperature != null)     cfg.setTemperature(temperature);
        if (maxTokens != null)       cfg.setMaxTokens(maxTokens);
        if (rateLimitPerDay != null) cfg.setRateLimitPerDay(rateLimitPerDay);
        cfg.setUpdatedAt(LocalDateTime.now());
        cfg.setUpdatedByMemberId(updatedByMemberId);
        return cfg;
    }
}
