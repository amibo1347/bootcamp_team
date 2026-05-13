package com.team.intranet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.team.intranet.enums.Visibility;
import com.team.intranet.enums.RepeatType;

@Entity
@Table(name = "tbl_calendar")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Calendar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "calendar_id")
    private Long calendarId; // 일정 id (PK)

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member; // 일정 작성자 (Member 엔티티 참조)

    @Column(name = "title", nullable = false)
    private String title; // 일정 제목

    @Lob
    @Column(name = "description", columnDefinition = "CLOB")
    private String description; // 일정 상세 설명

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt; // 시작 일시

    @Column(name = "end_at")
    private LocalDateTime endAt; // 종료 일시

    @Column(name = "all_day")
    private boolean allDay; // 종일 일정 여부

    @Column(name = "is_repeat")
    private boolean isRepeat; // 반복 일정 여부

    @Enumerated(EnumType.STRING)
    @Column(name = "repeat_type")
    private RepeatType repeatType; // 반복 주기(DAILY/WEEKLY/MONTHLY/YEARLY)

    @Column(name = "repeat_end_at")
    private LocalDateTime repeatEndAt; // 반복 종료 일시 (이 시점까지만 반복 생성)

    /**
     * 매주 반복 시 어느 요일에 반복할지 비트마스크.
     * MON=1, TUE=2, WED=4, THU=8, FRI=16, SAT=32, SUN=64 (합산).
     * 예: 월·수·금 = 1 + 4 + 16 = 21.
     * null/0 이면 시작일의 요일만 사용(단일 요일 반복).
     */
    @Column(name = "repeat_weekdays")
    private Integer repeatWeekdays;

    /**
     * 매월 반복 시 어느 일자에 반복할지 비트마스크 (1일 = 1<<0, 31일 = 1<<30).
     * 예: 8일·21일 = (1<<7) | (1<<20) = 128 + 1048576 = 1048704.
     * null/0 이면 시작일의 일자만 사용(단일 일자 반복).
     */
    @Column(name = "repeat_month_days")
    private Integer repeatMonthDays;

    @Column(name = "is_alert")
    private boolean isAlert; // 알림 사용 여부 (프로젝트 내 알림창 연동용 플래그)

    @Column(name = "alert_minutes_before")
    private Integer alertMinutesBefore; // 일정 시작 몇 분 전에 알림을 띄울지 (분 단위 숫자)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Category category; // 일정 카테고리 (Category 엔티티 참조)

    @Column(name = "location")
    private String location; // 장소

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false)
    private Visibility visibility; // 공개 범위(PRIVATE/COMPANY/DEPARTMENT/SPECIFIC)

    @Column(name = "created_at")
    private LocalDateTime createdAt; // 생성 일시

}
