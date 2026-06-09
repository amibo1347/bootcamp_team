package com.team.intranet.scheduler;

import java.time.LocalDate;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.team.intranet.entity.Member;
import com.team.intranet.enums.member.Status;
import com.team.intranet.repository.MemberRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 휴직 자동 복귀 스케줄러.
 *  - 매시간 정각마다 leaveExpectedReturnDate 가 도래한 ON_LEAVE 회원을 JOIN 으로 복귀.
 *  - 관리자가 그 전에 수동으로 복귀(reinstate) 한 회원은 이미 status=JOIN 이라 대상에서 제외됨.
 *  - 시간 단위로 도는 이유: 자정 정각 도달과 무관하게 복귀일이 '오늘' 인 회원을 빠르게 흡수.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeaveReinstateScheduler {

    private final MemberRepository memberRepository;

    @Scheduled(cron = "0 0 * * * *") // 매시간 정각
    @Transactional
    public void autoReinstateOnReturnDate() {
        LocalDate today = LocalDate.now();
        // 단순 전체 ON_LEAVE 스캔 — 회원 수가 작아서 인덱스 쿼리 없이도 충분.
        // (대규모 운영 시 leave_expected_return_date <= today 인덱스 쿼리로 교체.)
        List<Member> onLeave = memberRepository.findAll().stream()
            .filter(m -> m.getStatus() == Status.ON_LEAVE)
            .filter(m -> m.getLeaveExpectedReturnDate() != null)
            .filter(m -> !today.isBefore(m.getLeaveExpectedReturnDate()))
            .toList();
        for (Member m : onLeave) {
            log.info("auto-reinstate memberId={} (expectedReturnDate={})",
                m.getMemberId(), m.getLeaveExpectedReturnDate());
            m.reinstate();
        }
    }
}
