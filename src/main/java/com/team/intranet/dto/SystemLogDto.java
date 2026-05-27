package com.team.intranet.dto;

import java.time.format.DateTimeFormatter;

import com.team.intranet.entity.SystemLog;
import com.team.intranet.entity.SystemLogSummary;
import com.team.intranet.enums.SystemLogAction;

import lombok.Builder;

/**
 * ADMIN 시스템 로그 페이지가 화면에 그릴 한 줄 행.
 *  - kind="RAW" 일 때 actor/action/target* 채워짐.
 *  - kind="SUMMARY" 일 때 actor 없이 summary 텍스트 + rawCount 만 채워짐 (요약 row).
 *  - 한 페이지에 raw 와 summary 가 섞이지 않도록 컨트롤러에서 모드 분기.
 */
@Builder
public record SystemLogDto(
        String kind,            // "RAW" | "SUMMARY"
        Long id,
        String createdAt,
        String actorName,
        String actionCode,
        String actionLabel,
        String targetType,
        Long targetId,
        String targetLabel,
        String detail,
        // SUMMARY 전용
        String periodType,
        String periodStart,
        String periodEnd,
        String summary,
        Long rawCount
) {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter D  = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static SystemLogDto from(SystemLog l) {
        SystemLogAction a = l.getAction();
        return SystemLogDto.builder()
                .kind("RAW")
                .id(l.getLogId())
                .createdAt(l.getCreatedAt() != null ? l.getCreatedAt().format(DT) : null)
                .actorName(l.getActorName())
                .actionCode(a != null ? a.name() : null)
                .actionLabel(a != null ? a.getLabel() : null)
                .targetType(l.getTargetType())
                .targetId(l.getTargetId())
                .targetLabel(l.getTargetLabel())
                .detail(l.getDetail())
                .build();
    }

    public static SystemLogDto from(SystemLogSummary s) {
        return SystemLogDto.builder()
                .kind("SUMMARY")
                .id(s.getSummaryId())
                .createdAt(s.getCreatedAt() != null ? s.getCreatedAt().format(DT) : null)
                .periodType(s.getPeriodType() != null ? s.getPeriodType().name() : null)
                .periodStart(s.getPeriodStart() != null ? s.getPeriodStart().format(D) : null)
                .periodEnd(s.getPeriodEnd() != null ? s.getPeriodEnd().format(D) : null)
                .summary(s.getSummary())
                .rawCount(s.getRawCount())
                .build();
    }
}
