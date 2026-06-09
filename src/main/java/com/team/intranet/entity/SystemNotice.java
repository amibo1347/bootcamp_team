package com.team.intranet.entity;

import java.time.LocalDateTime;

import com.team.intranet.enums.SystemNoticeType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 전(全) 회사 대상 시스템 공지 / 점검 안내.
 *  - MASTER 가 등록. 노출 기간(startsAt ~ endsAt) 안에 있으면 모든 회사의 회원 화면에 배너로 표시.
 *  - endsAt 이 null 이면 무기한 노출.
 */
@Entity
@Table(name = "tbl_system_notice")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class SystemNotice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notice_id")
    private Long noticeId;

    @Column(name = "content", nullable = false, length = 1000)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "notice_type", nullable = false, length = 20)
    private SystemNoticeType noticeType;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    /** null 이면 무기한. */
    @Column(name = "ends_at")
    private LocalDateTime endsAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static SystemNotice create(String content, SystemNoticeType noticeType,
                                      LocalDateTime startsAt, LocalDateTime endsAt) {
        return SystemNotice.builder()
                .content(content)
                .noticeType(noticeType)
                .startsAt(startsAt)
                .endsAt(endsAt)
                .createdAt(LocalDateTime.now())
                .build();
    }

    /** 지정 시각 기준 노출 대상인지. */
    public boolean isVisibleAt(LocalDateTime now) {
        if (startsAt != null && startsAt.isAfter(now)) return false;
        if (endsAt != null && endsAt.isBefore(now)) return false;
        return true;
    }
}
