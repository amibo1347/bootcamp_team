package com.team.intranet.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 시스템 전체 점검 모드 (글로벌 싱글톤).
 *  - 모든 회사를 한꺼번에 점검 상태로 전환하기 위한 1 row 짜리 설정. AiConfig 와 같은 패턴.
 *  - enabled=true 면 즉시 점검 모드 (수동 토글).
 *  - 또는 startsAt/endsAt 로 예약 — isCurrentlyOn() 헬퍼가 두 조건을 종합 판단한다.
 *  - 모든 변경은 MASTER 만 가능. updatedByMasterId 로 누구의 조작인지 감사 기록.
 *
 *  ※ MASTER 콘솔(/master/**) 은 점검 중에도 통과 — MASTER 가 점검 OFF 할 수 있어야 한다.
 *  ※ 점검 안내 페이지(/maintenance) 와 점검 상태 폴링 API(/api/system-maintenance/current) 는 비로그인도 접근.
 */
@Entity
@Table(name = "tbl_system_maintenance")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemMaintenance {

    /** 싱글톤 식별자 — 항상 1L. */
    public static final Long SINGLETON_ID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "maintenance_id")
    private Long maintenanceId;

    /** 수동 토글 — true 면 즉시 점검 모드 (예약과 무관하게 ON). */
    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    /** 예약 시작 — null 이면 예약 없음. */
    @Column(name = "starts_at")
    private LocalDateTime startsAt;

    /** 예약 종료 — null 이면 무기한 (수동 OFF 까지). */
    @Column(name = "ends_at")
    private LocalDateTime endsAt;

    /** 점검 사유/안내문. 회원에게 노출됨 — 외부 노출 금지 정보 작성 X. */
    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 마지막으로 변경한 MASTER 계정 id. 감사용. */
    @Column(name = "updated_by_master_id")
    private Long updatedByMasterId;

    /**
     * 지금 점검 중인지 — 수동 토글 OR 예약 시간대.
     *  - enabled=true → 즉시 ON.
     *  - 또는 (startsAt 도래 + 아직 endsAt 미경과) → ON.
     *  - startsAt 만 있고 endsAt 가 null 이면 startsAt 도래 후 무기한 ON.
     */
    public boolean isCurrentlyOn(LocalDateTime now) {
        if (enabled) return true;
        if (startsAt == null) return false;
        if (now.isBefore(startsAt)) return false;
        if (endsAt != null && !now.isBefore(endsAt)) return false;
        return true;
    }

    /** 비활성 상태의 기본 row (싱글톤 lazy 생성용). */
    public static SystemMaintenance off() {
        return SystemMaintenance.builder()
            .enabled(false)
            .updatedAt(LocalDateTime.now())
            .build();
    }
}
