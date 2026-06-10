package com.team.intranet.service.ai;

import java.time.Year;

import org.springframework.stereotype.Service;

import com.team.intranet.dto.leave.LeaveBalanceSummary;
import com.team.intranet.service.LeaveBalanceService;
import com.team.intranet.session.MemberSession;

import lombok.RequiredArgsConstructor;

/**
 * AI 비서가 "남은 연차 며칠?" 같은 질문에 답할 때 참고할 본인 연차 현황 컨텍스트.
 *  - 본인 데이터만 (회사/회원 격리) → 회원이 화면에서 보는 잔여 연차와 동일한 수치.
 *  - 잔여 = 부여 + 조정 - 사용. 신청중은 아직 미차감(참고용).
 */
@Service
@RequiredArgsConstructor
public class AiLeaveContextService {

    private final LeaveBalanceService leaveBalanceService;

    public String buildContext(MemberSession ms) {
        if (ms == null || ms.getCompanyId() == null || ms.getMemberId() == null) return "";

        LeaveBalanceSummary s;
        try {
            s = leaveBalanceService.getMySummary(
                    ms.getCompanyId(), ms.getMemberId(), Year.now().getValue());
        } catch (Exception e) {
            return "";
        }
        if (s == null) return "";

        return "\n\n[내 연차 현황 — " + s.getYear() + "년 (회계연도 기준, 단위: 일)]\n"
            + "- 올해 부여: " + fmt(s.getGranted()) + "\n"
            + "- 사용(승인 완료): " + fmt(s.getUsed()) + "\n"
            + "- 신청 중(미승인, 아직 미차감): " + fmt(s.getPending()) + "\n"
            + "- 남은(잔여) 연차: " + fmt(s.getRemaining()) + "\n"
            + "※ 사용자가 \"남은/잔여 연차\", \"연차 며칠 남았어\" 등을 물으면 위 '남은(잔여) 연차' 수치로만 답하세요.\n"
            + "  반차는 0.5일로 계산됩니다. 위 수치는 연차유급휴가 기준이며 병가·경조사 등 다른 휴가는 포함하지 않습니다.\n";
    }

    /** 0.5 단위 표시 — 정수면 소수점 제거(예: 12.0 → 12, 12.5 → 12.5). */
    private static String fmt(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v) + "일";
        return String.valueOf(v) + "일";
    }
}
