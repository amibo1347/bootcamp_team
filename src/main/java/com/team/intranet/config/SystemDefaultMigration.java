package com.team.intranet.config;

import java.util.List;
import java.util.Optional;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.team.intranet.entity.Company;
import com.team.intranet.entity.Dept;
import com.team.intranet.entity.Member;
import com.team.intranet.entity.Position;
import com.team.intranet.enums.member.Role;
import com.team.intranet.repository.CompanyRepository;
import com.team.intranet.repository.MemberRepository;
import com.team.intranet.repository.PositionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 기존 회사 데이터에 isSystem 디폴트 플래그를 1회성으로 채우는 마이그레이션.
 *  - 각 회사의 Role.ADMIN 직급(가장 낮은 positionId) → Position.isSystem=true
 *  - 그 대표 회원의 Dept → Dept.isSystem=true
 *  - 이미 true 인 경우 skip → idempotent (반복 실행 안전).
 *  - 새 회사는 CompanyService.create 에서 createSystemPosition/createSystemDept 로 직접 생성되므로
 *    이 마이그레이션은 ddl-auto 가 컬럼을 추가한 직후 기존 row 들을 한 번 보정하는 용도.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class SystemDefaultMigration implements ApplicationRunner {

    /** "높을수록 상위" 의미에 맞춘 시스템 대표 직급의 표준 level. */
    private static final int ADMIN_SYSTEM_LEVEL = 99;

    private final CompanyRepository companyRepository;
    private final PositionRepository positionRepository;
    private final MemberRepository memberRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Company> companies = companyRepository.findAll();
        int positionFlagged = 0;
        int deptFlagged = 0;
        int levelBumped = 0;

        for (Company company : companies) {
            Long companyId = company.getCompanyId();

            // 1) Role.ADMIN 직급 중 가장 낮은 positionId(= 회사 생성 시 만든 원본 "대표") 1개를 시스템 보호.
            Optional<Position> adminPosition = positionRepository
                    .findAllByCompanyCompanyId(companyId).stream()
                    .filter(p -> p.getRole() == Role.ADMIN)
                    .min((a, b) -> Long.compare(a.getPositionId(), b.getPositionId()));

            if (adminPosition.isPresent()) {
                Position position = adminPosition.get();
                if (!position.isSystemDefault()) {
                    position.setIsSystem(Boolean.TRUE);
                    positionFlagged++;
                }

                // 2) "높을수록 상위" 의미로 정렬을 통일하기 위해 시스템 대표 직급을 항상 level=99 로 보정.
                //    - 과거에는 level=1 만 99 로 올렸으나, 신규 정책(CompanyService.create 가 항상 99 로 생성) 도입
                //      이후 만들어진 회사들과 일관성을 맞추기 위해 99 가 아닌 모든 값을 99 로 보정한다.
                //    - 동일 회사에 이미 level=99 인 다른 직급이 있으면 UNIQUE 충돌 회피를 위해 skip + 경고.
                Integer currentLevel = position.getPositionLevel();
                if (currentLevel != null && currentLevel != ADMIN_SYSTEM_LEVEL) {
                    boolean conflict = positionRepository
                            .existsByCompanyCompanyIdAndPositionLevelAndPositionIdNot(
                                    companyId, ADMIN_SYSTEM_LEVEL, position.getPositionId());
                    if (conflict) {
                        log.warn("[SystemDefaultMigration] companyId={} 대표 직급(positionId={}) level {}→{} 보정 skip: 동일 level 충돌",
                                companyId, position.getPositionId(), currentLevel, ADMIN_SYSTEM_LEVEL);
                    } else {
                        position.setPositionLevel(ADMIN_SYSTEM_LEVEL);
                        levelBumped++;
                    }
                }
            }

            // 3) 대표 회원(가장 낮은 memberId 의 ADMIN)의 Dept 를 시스템 보호.
            List<Member> admins = memberRepository
                    .findByCompany_CompanyIdAndRoleOrderByMemberIdAsc(companyId, Role.ADMIN);
            if (!admins.isEmpty()) {
                Dept dept = admins.get(0).getDept();
                if (dept != null && !dept.isSystemDefault()) {
                    dept.setIsSystem(Boolean.TRUE);
                    deptFlagged++;
                }
            }
        }

        if (positionFlagged > 0 || deptFlagged > 0 || levelBumped > 0) {
            log.info("[SystemDefaultMigration] flagged {} positions, {} depts as isSystem; bumped {} admin levels 1→{}",
                    positionFlagged, deptFlagged, levelBumped, ADMIN_SYSTEM_LEVEL);
        }
    }
}
