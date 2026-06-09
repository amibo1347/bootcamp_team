package com.team.intranet.scheduler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;

import com.team.intranet.repository.MemberRepository;
import com.team.intranet.enums.member.Status;
import com.team.intranet.entity.Member;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemberRetentionScheduler {

    private final MemberRepository memberRepository;

    // LEAVE/BANNED 회원의 보존 기간(2년/5년) 경과 시 row 영구 삭제.
    // 작성했던 게시글의 author FK 는 ON DELETE SET NULL 로 자동 NULL 화되며,
    // author_display_name 이 남아 '탈퇴 회원' 으로 계속 표시된다.
    // REJECT 는 상태 전이 시점에 이미 즉시 삭제되므로 여기서 처리하지 않는다.
    @Scheduled(cron = "*/30 * * * * *")
    @Transactional
    public void purgeExpiredTerminalMembers() {
        LocalDateTime now = LocalDateTime.now();
        for (Status s : List.of(Status.LEAVE, Status.BANNED)) {
            LocalDateTime threshold = now.minusDays(s.getRetentionDays());
            List<Member> targets = memberRepository
                .findByStatusAndStatusChangedAtBefore(s, threshold);
            for (Member m : targets) {
                log.info("permanently deleting memberId={}, status={}, statusChangedAt={}",
                    m.getMemberId(), s, m.getStatusChangedAt());
                memberRepository.delete(m);
            }
        }
    }
}
