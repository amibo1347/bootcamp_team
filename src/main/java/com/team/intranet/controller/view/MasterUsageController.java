package com.team.intranet.controller.view;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttribute;

import com.team.intranet.dto.CompanyUsageDto;
import com.team.intranet.dto.MasterUsageSummaryDto;
import com.team.intranet.dto.UsageTrendPointDto;
import com.team.intranet.service.MasterStatsService;
import com.team.intranet.session.MasterSession;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * MASTER 회사 사용량 대시보드.
 *  - 접근 제어: masterSecurityFilterChain + MasterTotpGuardFilter.
 *  - 페이지 렌더에 KPI summary + 회사별 행 + 30일 추이 데이터를 모두 함께 내려준다.
 *  - CSV 내보내기는 같은 데이터를 OutputStream 으로 흘려보내는 별도 GET 엔드포인트.
 */
@Controller
@RequestMapping("/master/usage")
@RequiredArgsConstructor
public class MasterUsageController {

    /** 기본 시계열 윈도우 — 차트 X축. */
    private static final int DEFAULT_TREND_DAYS = 30;

    private final MasterStatsService masterStatsService;

    @GetMapping({"", "/"})
    public String usage(@SessionAttribute(name = "masterSession", required = false) MasterSession master,
                        Model model) {
        if (master == null) return "redirect:/master/login";

        List<CompanyUsageDto> rows = masterStatsService.usageList();
        MasterUsageSummaryDto summary = masterStatsService.summary();
        List<UsageTrendPointDto> trend = masterStatsService.dailyTrend(DEFAULT_TREND_DAYS);

        model.addAttribute("master", master);
        model.addAttribute("summary", summary);
        model.addAttribute("rows", rows);
        model.addAttribute("trend", trend);
        model.addAttribute("trendDays", DEFAULT_TREND_DAYS);
        model.addAttribute("generatedAt", LocalDateTime.now());
        return "master/usage";
    }

    /**
     * 회사별 사용량 CSV 내보내기.
     *  - 한글 컬럼명을 위해 UTF-8 BOM 을 앞에 붙여 Excel 에서 깨지지 않게 한다.
     *  - 파일명은 RFC 5987 형식(filename*) 으로 UTF-8 인코딩 + 호환용 ASCII filename 도 함께 제공.
     */
    @GetMapping("/export.csv")
    public void exportCsv(@SessionAttribute(name = "masterSession", required = false) MasterSession master,
                          @RequestParam(name = "search", required = false) String search,
                          HttpServletResponse response) throws IOException {
        if (master == null) {
            response.sendRedirect("/master/login");
            return;
        }

        List<CompanyUsageDto> rows = masterStatsService.usageList();
        String keyword = search == null ? "" : search.trim().toLowerCase();
        if (!keyword.isEmpty()) {
            rows = rows.stream()
                       .filter(r -> r.getCompanyName() != null
                                 && r.getCompanyName().toLowerCase().contains(keyword))
                       .toList();
        }

        String filename = "company-usage_" + LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv";
        String filenameUtf8 = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");

        response.setContentType(MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + filenameUtf8);

        try (OutputStream os = response.getOutputStream()) {
            // UTF-8 BOM — Excel 한글 깨짐 방지.
            os.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            try (Writer w = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
                w.write("회사ID,회사명,상태,회원 수,게시글 수,결재 수,스토리지(Bytes),이번달 신규,마지막 활동\n");
                DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                for (CompanyUsageDto r : rows) {
                    w.write(String.valueOf(r.getCompanyId()));
                    w.write(',');
                    w.write(escape(r.getCompanyName()));
                    w.write(',');
                    w.write(r.isActive() ? "활성" : "비활성");
                    w.write(',');
                    w.write(String.valueOf(r.getMemberCount()));
                    w.write(',');
                    w.write(String.valueOf(r.getArticleCount()));
                    w.write(',');
                    w.write(String.valueOf(r.getApprovalCount()));
                    w.write(',');
                    w.write(String.valueOf(r.getStorageBytes()));
                    w.write(',');
                    w.write(String.valueOf(r.getNewMembersThisMonth()));
                    w.write(',');
                    w.write(r.getLastActivityAt() == null ? "" : r.getLastActivityAt().format(df));
                    w.write('\n');
                }
            }
        }
    }

    /** CSV 셀 이스케이프 — 쉼표/따옴표/개행 포함 시 큰따옴표 감싸고 내부 " 는 "" 으로. */
    private static String escape(String s) {
        if (s == null) return "";
        boolean needsQuote = s.indexOf(',') >= 0 || s.indexOf('"') >= 0
                          || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
        if (!needsQuote) return s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
