package com.team.intranet.controller.api;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.multipart.MultipartFile;

import com.team.intranet.dto.ArticleUnifiedTrashDto;
import com.team.intranet.service.ArticleService;
import com.team.intranet.service.MemberService;
import com.team.intranet.config.AuthenticatedMember;
import com.team.intranet.session.MemberSession;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import com.team.intranet.enums.member.Status;

@RestController
@RequestMapping("/api/subAdmin")
@RequiredArgsConstructor
public class SubAdminApiController {

    private final MemberService memberService;
    private final ArticleService articleService;

    /**
     * 통합 휴지통: 소프트 삭제 글 목록.
     * ※ 모든 인증된 회원이 호출 가능. 권한 분기는 ArticleService 에서 처리한다
     *   — TRASH_MANAGEMENT 보유자는 회사 전체, 그 외는 본인 작성 글만.
     */
    @GetMapping("/articles/trash")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<ArticleUnifiedTrashDto>> listUnifiedTrash(
            @AuthenticatedMember MemberSession ms,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(articleService.findDeletedArticlesForCompanyUnified(ms, pageable));
    }

    private LocalDateTime parseBirthDay(String input) {
      if (input == null || input.isBlank()) return null;
      String digits = input.replaceAll("-", "");           // "2024-02-12" → "20240212"
      LocalDate date = LocalDate.parse(digits, DateTimeFormatter.ofPattern("yyyyMMdd"));
      return date.atStartOfDay();
  }

    // 가입 승인용 포스트매핑
    @PostMapping("/accept/{id}")
    @PreAuthorize("hasRole('SUB_ADMIN') or hasRole('ADMIN')")
    public String approveMember(
            @PathVariable("id") Long memberId,
            @RequestParam Long deptId,
            @RequestParam Long positionId,
            @SessionAttribute("memberSession") MemberSession ms) {


        memberService.acceptMember(ms, memberId, deptId, positionId);
        return "redirect:/admin/waitingList";
    }

    // 회원 정보 수정 (부서, 직급 변경)
    @PostMapping("/update/{id}")
    @PreAuthorize("hasRole('SUB_ADMIN') or hasRole('ADMIN')")
    public String updateMember(
            @PathVariable("id") Long memberId,
            @RequestParam Long deptId,
            @RequestParam Long positionId,
            @RequestParam(value = "profileImg", required = false) MultipartFile profileImg,
            @RequestParam String name,
            @RequestParam String email,
            @RequestParam String phone,
            @RequestParam String birthDay,
            HttpSession session) throws IOException {
        
        MemberSession ms = (MemberSession) session.getAttribute("memberSession");

        byte[] imgBytes = (profileImg != null && !profileImg.isEmpty()) ? profileImg.getBytes() : null;
        
        memberService.updateMemberInfo(ms, memberId, deptId, positionId, imgBytes, phone, email, name, parseBirthDay(birthDay));

        return "redirect:/admin/memberList";
    }

    // 인사이동: 다수 회원의 부서/직급을 일괄 변경
    @PostMapping("/reassign")
    @PreAuthorize("hasRole('SUB_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<Void> reassignMembers(
            @RequestParam("memberIds") List<Long> memberIds,
            @RequestParam(value = "deptId", required = false) Long deptId,
            @RequestParam(value = "positionId", required = false) Long positionId,
            @SessionAttribute("memberSession") MemberSession ms) {

        memberService.reassignMembers(ms, memberIds, deptId, positionId);
        return ResponseEntity.ok().build();
    }

    // 상태 처리
    @PostMapping("/status/{id}/{action}")
    @PreAuthorize("hasRole('SUB_ADMIN') or hasRole('ADMIN')")
    public String changeStatus(@PathVariable("id") Long memberId,
                             @PathVariable("action") Status action,
                             @SessionAttribute("memberSession") MemberSession ms) {

        memberService.changeStatus(ms, memberId, action);

        return "redirect:/admin/memberList";
    }

    /**
     * 직원 비밀번호 초기화 (수정 모달 → "보안" 섹션 → [비밀번호 초기화]).
     *  - 같은 회사 직원만 대상 (서비스에서 검증).
     *  - 응답으로 평문 임시 비번을 1회 노출 → 관리자가 직원에게 직접 전달.
     *  - MASTER 의 회사 대표 비번 초기화(/master/companies/{id}/admin/reset-password) 와 동일 패턴.
     */
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('SUB_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<java.util.Map<String, Object>> resetMemberPassword(
            @PathVariable("id") Long memberId,
            @AuthenticatedMember MemberSession ms) {
        try {
            String tempPassword = memberService.resetMemberPassword(ms, memberId);
            return ResponseEntity.ok(java.util.Map.of(
                "success", true,
                "tempPassword", tempPassword,
                "message", "임시 비밀번호가 발급되었습니다. 해당 직원에게 직접 전달하세요."
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("success", false, "message", e.getMessage()));
        }
    }

}
