package com.team.intranet.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team.intranet.entity.SystemMaintenance;
import com.team.intranet.repository.SystemMaintenanceRepository;

import lombok.RequiredArgsConstructor;

/**
 * 시스템 점검 모드 운영.
 *  - 글로벌 싱글톤 1 row 를 관리. 없으면 OFF 상태로 lazy 생성.
 *  - 수동 토글 (즉시 ON/OFF) + 예약 (startsAt/endsAt) 둘 다 지원.
 *  - isCurrentlyOn() 은 필터에서 매 요청 호출되므로 read-only 트랜잭션으로 가볍게.
 */
@Service
@RequiredArgsConstructor
public class SystemMaintenanceService {

    private final SystemMaintenanceRepository repository;

    /** 싱글톤 row 가져오기 — 없으면 OFF 상태로 만들어 반환. */
    @Transactional
    public SystemMaintenance getCurrent() {
        return repository.findById(SystemMaintenance.SINGLETON_ID)
            .orElseGet(() -> repository.save(SystemMaintenance.off()));
    }

    /** 가벼운 read-only 조회 — 필터에서 매 요청 호출. */
    @Transactional(readOnly = true)
    public boolean isCurrentlyOn() {
        return repository.findById(SystemMaintenance.SINGLETON_ID)
            .map(m -> m.isCurrentlyOn(LocalDateTime.now()))
            .orElse(false);
    }

    /**
     * 수동 토글 — enabled 만 즉시 변경. reason 도 함께 갱신 (null 허용 시 기존값 유지하지 않고 그대로 덮어씀 — 명시적 의도).
     *  - 예약(startsAt/endsAt) 은 건드리지 않음. 따로 schedule() 로 변경.
     */
    @Transactional
    public SystemMaintenance toggle(boolean enabled, String reason, Long masterId) {
        SystemMaintenance m = getCurrent();
        m.setEnabled(enabled);
        m.setReason(blankToNull(reason));
        m.setUpdatedAt(LocalDateTime.now());
        m.setUpdatedByMasterId(masterId);
        return m;
    }

    /**
     * 예약 — startsAt / endsAt 둘 다 또는 한 쪽만 설정.
     *  - 양쪽 null 이면 예약 제거 (수동 토글만 효력).
     *  - endsAt 이 startsAt 보다 이전이면 거절.
     */
    @Transactional
    public SystemMaintenance schedule(LocalDateTime startsAt, LocalDateTime endsAt,
                                      String reason, Long masterId) {
        if (startsAt != null && endsAt != null && endsAt.isBefore(startsAt)) {
            throw new IllegalArgumentException("종료 시각이 시작 시각보다 빠를 수 없습니다.");
        }
        SystemMaintenance m = getCurrent();
        m.setStartsAt(startsAt);
        m.setEndsAt(endsAt);
        m.setReason(blankToNull(reason));
        m.setUpdatedAt(LocalDateTime.now());
        m.setUpdatedByMasterId(masterId);
        return m;
    }

    /** 예약 제거 (startsAt/endsAt 만 null 로). 수동 enabled 는 유지. */
    @Transactional
    public SystemMaintenance clearSchedule(Long masterId) {
        SystemMaintenance m = getCurrent();
        m.setStartsAt(null);
        m.setEndsAt(null);
        m.setUpdatedAt(LocalDateTime.now());
        m.setUpdatedByMasterId(masterId);
        return m;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
