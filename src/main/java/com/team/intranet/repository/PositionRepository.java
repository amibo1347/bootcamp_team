package com.team.intranet.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team.intranet.entity.Position;

public interface  PositionRepository extends JpaRepository<Position, Long> {
    List<Position> findAllByCompanyCompanyId(Long companyId);

    /**
     * 회사의 직급을 level 오름차순으로 조회. 동일 level 은 이름·ID 순 안정 정렬.
     *  - 동일 level 허용 정책(예: 인사팀 부장, 개발팀 부장)에 따라 보조 정렬 필요.
     */
    @Query("SELECT p FROM Position p WHERE p.company.companyId = :companyId "
            + "ORDER BY p.positionLevel ASC, p.positionName ASC, p.positionId ASC")
    List<Position> findByCompany_CompanyIdOrderByPositionLevelAsc(@Param("companyId") Long companyId);

    /**
     * 회사의 직급을 level 내림차순으로 조회. 동일 level 은 이름·ID 순 안정 정렬.
     */
    @Query("SELECT p FROM Position p WHERE p.company.companyId = :companyId "
            + "ORDER BY p.positionLevel DESC, p.positionName ASC, p.positionId ASC")
    List<Position> findByCompany_CompanyIdOrderByPositionLevelDesc(@Param("companyId") Long companyId);

    /** SystemDefaultMigration 의 대표 level 보정에서 충돌 체크용. 일반 CRUD 에선 사용 안 함. */
    boolean existsByCompanyCompanyIdAndPositionLevelAndPositionIdNot(
        Long companyId,
        Integer positionLevel,
        Long positionId
    );
}
