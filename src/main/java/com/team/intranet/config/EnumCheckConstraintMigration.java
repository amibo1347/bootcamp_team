package com.team.intranet.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * enum 컬럼의 stale CHECK 제약 제거 (1회성).
 *
 * 배경:
 *  - Hibernate 6 은 @Enumerated(STRING) 컬럼에 값 목록 CHECK 제약을 자동 생성한다
 *    (예: permission in ('WAITING_APPROVAL', 'MEMBER_MANAGEMENT', ...)).
 *  - ddl-auto=update 는 enum 에 값을 "추가"해도 기존 CHECK 제약을 갱신하지 않는다.
 *  - 따라서 새 enum 값(예: VACATION_MANAGE)을 INSERT 하면 Oracle 이 옛 CHECK 제약 위반(ORA-02290)으로
 *    거부 → 권한 저장 시 500.
 *
 * 해결:
 *  - 대상 컬럼의 "값 목록 CHECK 제약"만 찾아 DROP. (NOT NULL 도 CHECK 로 표현되므로, search_condition 에
 *    알려진 enum 리터럴이 포함된 것만 골라 NOT NULL 은 보존.)
 *  - 한 번 제거하면 update 모드는 다시 만들지 않으므로 이후 enum 값 추가도 안전.
 *  - 운영 영향 없음: 값 검증은 애플리케이션(@Enumerated)에서 수행.
 */
@Slf4j
@Component
@Order(3) // 다른 마이그레이션 이후
@RequiredArgsConstructor
public class EnumCheckConstraintMigration implements ApplicationRunner {

    private final DataSource dataSource;

    /** {테이블, 컬럼, 해당 컬럼 CHECK 에 반드시 들어있는 enum 리터럴(NOT NULL 과 구분용)} */
    private static final String[][] TARGETS = {
        { "TBL_POSITION_PERMISSION",     "PERMISSION", "WAITING_APPROVAL" },
        { "TBL_MEMBER_EXTRA_PERMISSION", "PERMISSION", "WAITING_APPROVAL" },
    };

    @Override
    public void run(ApplicationArguments args) {
        try (Connection con = dataSource.getConnection()) {
            int dropped = 0;
            for (String[] t : TARGETS) {
                dropped += dropEnumCheck(con, t[0], t[1], t[2]);
            }
            if (dropped > 0) {
                log.info("[EnumCheckConstraintMigration] dropped {} stale enum CHECK constraint(s)", dropped);
            }
        } catch (Exception e) {
            // Oracle 외 DB·권한 부족 등은 조용히 skip (기능에 치명적이지 않음).
            log.warn("[EnumCheckConstraintMigration] skipped: {}", e.getMessage());
        }
    }

    private int dropEnumCheck(Connection con, String table, String column, String marker) {
        String find =
            "SELECT c.constraint_name " +
            "FROM user_constraints c " +
            "JOIN user_cons_columns cc ON c.constraint_name = cc.constraint_name " +
            "WHERE c.constraint_type = 'C' " +
            "  AND c.table_name = ? " +
            "  AND cc.column_name = ? " +
            "  AND c.search_condition_vc LIKE ?";

        List<String> names = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(find)) {
            ps.setString(1, table);
            ps.setString(2, column);
            ps.setString(3, "%" + marker + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) names.add(rs.getString(1));
            }
        } catch (SQLException e) {
            // 테이블 미존재(아직 생성 전)·search_condition_vc 미지원 등 → skip.
            log.warn("[EnumCheckConstraintMigration] lookup skip for {}.{}: {}", table, column, e.getMessage());
            return 0;
        }

        int dropped = 0;
        for (String name : names) {
            try (Statement st = con.createStatement()) {
                st.execute("ALTER TABLE " + table + " DROP CONSTRAINT " + name);
                log.info("[EnumCheckConstraintMigration] dropped {} on {}.{}", name, table, column);
                dropped++;
            } catch (SQLException e) {
                log.warn("[EnumCheckConstraintMigration] drop failed {} on {}: {}", name, table, e.getMessage());
            }
        }
        return dropped;
    }
}
