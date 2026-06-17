package com.team.intranet.config;

import java.time.LocalDate;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.team.intranet.entity.Member;
import com.team.intranet.repository.MemberRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 기존 회원의 입사일(hireDate) 1회성 backfill.
 *  - hireDate 가 null 인 회원에게 승인일(acceptedAt) → 없으면 가입일(createdAt) 을 입사일로 채운다.
 *  - 둘 다 없으면 건너뜀(다음 기동 시 재시도). 이미 채워진 회원은 조회 대상에서 빠지므로 idempotent.
 *  - ddl-auto=update 가 hire_date 컬럼을 추가한 직후 기존 row 를 보정하는 용도.
 */
@Slf4j
@Component
@Order(2) // SystemDefaultMigration(@Order(1)) 이후
@RequiredArgsConstructor
public class HireDateBackfillMigration implements ApplicationRunner {

    private final MemberRepository memberRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Member> targets = memberRepository.findByHireDateIsNull();
        int filled = 0;
        for (Member m : targets) {
            LocalDate seed = null;
            if (m.getAcceptedAt() != null) {
                seed = m.getAcceptedAt().toLocalDate();
            } else if (m.getCreatedAt() != null) {
                seed = m.getCreatedAt().toLocalDate();
            }
            if (seed != null) {
                m.setHireDate(seed);
                filled++;
            }
        }
        if (filled > 0) {
            log.info("[HireDateBackfillMigration] backfilled hireDate for {} members", filled);
        }
    }
}
