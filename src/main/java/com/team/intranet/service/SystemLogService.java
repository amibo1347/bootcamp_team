package com.team.intranet.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.team.intranet.dto.SystemLogDto;
import com.team.intranet.entity.Company;
import com.team.intranet.entity.Member;
import com.team.intranet.entity.SystemLog;
import com.team.intranet.entity.SystemLogSummary;
import com.team.intranet.enums.ErrorCode;
import com.team.intranet.enums.SystemLogPeriodType;
import com.team.intranet.event.SystemLogEvent;
import com.team.intranet.exception.BusinessException;
import com.team.intranet.repository.CompanyRepository;
import com.team.intranet.repository.MemberRepository;
import com.team.intranet.repository.SystemLogRepository;
import com.team.intranet.repository.SystemLogSummaryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 시스템 로그 — 적재(Listener) + ADMIN 조회.
 *  - 적재는 {@link SystemLogEvent} 의 AFTER_COMMIT 리스너로 수행 — 본 트랜잭션 실패시 자동 미저장.
 *  - 리스너 INSERT 자체가 실패해도 본 비즈니스에는 영향 없음 (try/catch 로 흡수).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemLogService {

    /** 한 row 의 detail 컬럼 최대 길이 — 엔티티 length=1000 과 정합. */
    private static final int DETAIL_MAX = 1000;
    /** target_label 컬럼 길이 — 엔티티 length=200 과 정합. */
    private static final int LABEL_MAX = 200;
    /** actor_name 컬럼 길이 — 엔티티 length=100 과 정합. */
    private static final int ACTOR_NAME_MAX = 100;

    private final SystemLogRepository logRepository;
    private final SystemLogSummaryRepository summaryRepository;
    private final CompanyRepository companyRepository;
    private final MemberRepository memberRepository;

    /**
     * SystemLogEvent 수신 → SystemLog row 저장.
     *  - AFTER_COMMIT: 발행자 트랜잭션이 정상 커밋된 뒤에만 적재.
     *  - REQUIRES_NEW: 적재 자체는 별도 트랜잭션. 로그 INSERT 실패가 다른 후속 리스너에 영향 안 주도록.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSystemLogEvent(SystemLogEvent event) {
        try {
            persist(event);
        } catch (Exception e) {
            // 로깅 실패는 본 비즈니스를 깨뜨리지 않도록 흡수만. 운영자가 알 수 있게 로그만.
            log.warn("시스템 로그 적재 실패: companyId={}, action={}, target={}/{}",
                event.companyId(), event.action(), event.targetType(), event.targetId(), e);
        }
    }

    private void persist(SystemLogEvent e) {
        if (e.companyId() == null || e.action() == null) return;
        Company company = companyRepository.findById(e.companyId()).orElse(null);
        if (company == null) return;
        Member actor = e.actorMemberId() != null
                ? memberRepository.findById(e.actorMemberId()).orElse(null)
                : null;
        SystemLog row = SystemLog.builder()
                .company(company)
                .actor(actor)
                .actorName(clip(e.actorName(), ACTOR_NAME_MAX))
                .action(e.action())
                .targetType(e.targetType())
                .targetId(e.targetId())
                .targetLabel(clip(e.targetLabel(), LABEL_MAX))
                .detail(clip(e.detail(), DETAIL_MAX))
                .createdAt(LocalDateTime.now())
                .build();
        logRepository.save(row);
    }

    private static String clip(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    // ─── ADMIN 조회 ─────────────────────────────────────────────────

    /**
     * ADMIN 시스템 로그 페이지 — raw 모드 (보관 기간 안의 로그).
     *  - 발신자(ms) 가 ADMIN 인지는 컨트롤러/Security 가드에서 검증. 여기선 회사 격리만.
     */
    @Transactional(readOnly = true)
    public Page<SystemLogDto> findRawLogs(Long companyId, Pageable pageable) {
        return logRepository
                .findByCompany_CompanyIdOrderByCreatedAtDesc(companyId, pageable)
                .map(SystemLogDto::from);
    }

    /** ADMIN 시스템 로그 페이지 — 요약 모드 (DAY/MONTH/QUARTER). */
    @Transactional(readOnly = true)
    public Page<SystemLogDto> findSummaries(Long companyId, SystemLogPeriodType type, Pageable pageable) {
        return summaryRepository
                .findByCompany_CompanyIdAndPeriodTypeOrderByPeriodStartDesc(companyId, type, pageable)
                .map(SystemLogDto::from);
    }

    /** 페이지 진입 권한 검증 — Role.ADMIN 만. (Security 가드와 이중 안전망.) */
    public void assertAdmin(com.team.intranet.session.MemberSession ms) {
        if (ms == null) throw new BusinessException(ErrorCode.NO_AUTHORITY);
        if (ms.getRole() != com.team.intranet.enums.member.Role.ADMIN) {
            throw new BusinessException(ErrorCode.NO_AUTHORITY);
        }
    }

    /** 회사 ID 안전 추출(로그용). */
    public Optional<Long> safeCompanyId(com.team.intranet.session.MemberSession ms) {
        return Optional.ofNullable(ms).map(com.team.intranet.session.MemberSession::getCompanyId);
    }
}
