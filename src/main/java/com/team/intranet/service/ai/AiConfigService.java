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
        AiConfig cfg = repo.findFirstByOrderByConfigIdAsc()
            .orElseGet(() -> repo.save(AiConfig.defaultConfig()));
        // Google 이 gemini-2.0-flash 의 free tier 를 limit:0 으로 끊어 더 이상 무료 호출 불가.
        // 한 번 자동 교체 후엔 no-op.
        boolean changed = false;
        if ("gemini-2.0-flash".equals(cfg.getModelName())) {
            cfg.setModelName("gemini-2.5-flash-lite");
            changed = true;
        }
        // 이전 default(2048) 로 저장된 경우만 768 로 자동 조정. 의도적으로 다른 값이면 유지.
        if (Integer.valueOf(2048).equals(cfg.getMaxTokens())) {
            cfg.setMaxTokens(768);
            changed = true;
        }
        if (changed) cfg.setUpdatedAt(LocalDateTime.now());
        return cfg;
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
