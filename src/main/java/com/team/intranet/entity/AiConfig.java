package com.team.intranet.entity;

import java.time.LocalDateTime;

import com.team.intranet.enums.AiProvider;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * AI 모델 설정 (singleton 1 row).
 *  - MASTER 가 변경. 변경 즉시 다음 LLM 호출부터 적용.
 *  - API 키는 환경변수로 분리 (DB에 평문 저장 X).
 *  - 첫 부팅 시 default 값으로 자동 생성됨 (AiConfigService.getCurrent).
 */
@Entity
@Table(name = "tbl_ai_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "config_id")
    private Long configId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    private AiProvider provider;

    /** 예: "gemini-1.5-flash", "gemini-2.0-flash-exp", "llama-3.3-70b-versatile". */
    @Column(name = "model_name", nullable = false, length = 128)
    private String modelName;

    /** 0.0 ~ 2.0 (provider 별 권장 범위 다름). null 이면 provider default. */
    @Column(name = "temperature")
    private Double temperature;

    /** 응답 최대 토큰. null 이면 provider default. */
    @Column(name = "max_tokens")
    private Integer maxTokens;

    /** 회원당 일일 호출 한도. 무료 모델 quota 보호용. */
    @Column(name = "rate_limit_per_day")
    private Integer rateLimitPerDay;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 마지막 변경 회원 (감사용). */
    @Column(name = "updated_by_member_id")
    private Long updatedByMemberId;

    /**
     * 첫 부팅 시 자동 생성될 기본값.
     *  - gemini-2.0-flash: 무료 tier 일 1,500 req (gemini-2.5-flash 는 일 20 으로 제한적).
     *  - MASTER 가 /api/master/ai/config 로 언제든 변경 가능.
     */
    public static AiConfig defaultConfig() {
        return AiConfig.builder()
            .provider(AiProvider.GEMINI)
            .modelName("gemini-2.0-flash")
            .temperature(0.7)
            .maxTokens(2048)
            .rateLimitPerDay(50)
            .updatedAt(LocalDateTime.now())
            .build();
    }
}
