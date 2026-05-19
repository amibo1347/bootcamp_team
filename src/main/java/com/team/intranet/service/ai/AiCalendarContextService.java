package com.team.intranet.service.ai;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team.intranet.entity.Calendar;
import com.team.intranet.entity.CalendarShareMember;
import com.team.intranet.entity.Member;
import com.team.intranet.enums.member.Status;
import com.team.intranet.repository.CalendarRepository;
import com.team.intranet.repository.CalendarShareMemberRepository;
import com.team.intranet.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

/**
 * AI 비서가 일정 수정/삭제 제안 시 참고할 본인 일정 컨텍스트.
 *  - 본인 회원의 일정만 (회사 격리 자동 — Calendar.member 기준).
 *  - 범위: 어제부터 60일 후까지 (최근/예정 일정).
 *  - 각 일정의 calendarId 를 함께 첨부 → LLM 이 수정/삭제 시 그 id 를 JSON 에 포함.
 */
@Service
@RequiredArgsConstructor
public class AiCalendarContextService {

    private static final int FUTURE_DAYS  = 60;
    private static final int PAST_DAYS    = 1;
    private static final int MAX_ITEMS    = 30;
    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final CalendarRepository calendarRepository;
    private final MemberRepository memberRepository;
    private final CalendarShareMemberRepository calendarShareMemberRepository;

    @Transactional(readOnly = true)
    public String buildContext(Long memberId) {
        Member me = memberRepository.findById(memberId).orElse(null);
        if (me == null) return "";

        StringBuilder sb = new StringBuilder();

        // ── 1. 본인 일정 ───────────────────────────────────────────
        LocalDateTime from = LocalDate.now().minusDays(PAST_DAYS).atStartOfDay();
        LocalDateTime to   = LocalDate.now().plusDays(FUTURE_DAYS).atTime(23, 59);
        List<Calendar> events = calendarRepository.findByMemberAndStartAtBetween(me, from, to);

        sb.append("\n\n[본인 일정 (최근 + 향후 ").append(FUTURE_DAYS).append("일)]\n");
        if (events.isEmpty()) {
            sb.append("(등록된 일정 없음. 수정/삭제 요청 시 \"해당 일정이 없습니다\" 라고 답하세요.)\n");
        } else {
            sb.append("아래 일정만 수정/삭제 대상으로 인정합니다. id 를 그대로 사용하세요.\n");
            int n = 0;
            for (Calendar c : events) {
                if (n++ >= MAX_ITEMS) break;
                String start = c.getStartAt() != null ? c.getStartAt().format(FMT) : "?";
                String end   = c.getEndAt() != null   ? c.getEndAt().format(FMT)   : "";
                sb.append("- id=").append(c.getCalendarId())
                  .append(", \"").append(c.getTitle() != null ? c.getTitle() : "(제목없음)").append("\"")
                  .append(", ").append(start);
                if (!end.isBlank()) sb.append(" ~ ").append(end);
                if (c.getLocation() != null && !c.getLocation().isBlank()) {
                    sb.append(", 장소=").append(c.getLocation());
                }
                // 공유 대상 회원 (본인 제외)
                List<CalendarShareMember> shares = calendarShareMemberRepository.findAllByCalendar(c);
                if (!shares.isEmpty()) {
                    List<String> names = shares.stream()
                        .map(s -> s.getMember() != null ? s.getMember().getName() : null)
                        .filter(name -> name != null && !name.isBlank())
                        .filter(name -> !name.equals(me.getName()))
                        .toList();
                    if (!names.isEmpty()) {
                        sb.append(", 공유=[").append(String.join(", ", names)).append("]");
                    }
                }
                sb.append("\n");
            }
        }

        // ── 2. 회사 동료 명단 (일정 공유 매칭용) ─────────────────────
        if (me.getCompany() != null) {
            List<Member> coworkers = memberRepository
                .findByStatusAndCompanyCompanyId(Status.JOIN, me.getCompany().getCompanyId());
            sb.append("\n[회사 동료 명단 — 일정 공유 시 attendeeNames 에 이 이름을 그대로 사용]\n");
            int n = 0;
            for (Member m : coworkers) {
                if (m.getMemberId().equals(me.getMemberId())) continue; // 본인 제외
                if (n++ >= 30) break;  // 토큰 절약 — 30명까지
                String dept = m.getDept() != null ? m.getDept().getDeptName() : "미지정";
                sb.append("- ").append(m.getName()).append(" (").append(dept).append(")\n");
            }
            if (n == 0) sb.append("(동료 없음)\n");
        }

        return sb.toString();
    }
}
