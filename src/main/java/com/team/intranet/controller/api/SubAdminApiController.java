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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.team.intranet.dto.ArticleUnifiedTrashDto;
import com.team.intranet.service.ArticleService;
import com.team.intranet.service.MemberService;
import com.team.intranet.session.MemberSession;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import com.team.intranet.enums.member.Status;

@Controller
@RequestMapping("/api/subAdmin")
@RequiredArgsConstructor
public class SubAdminApiController {

    private final MemberService memberService;
    private final ArticleService articleService;

    /** 통합 휴지통: 회사 단위 소프트 삭제 글 목록 (SUB_ADMIN 계열 전용 경로 보호 후 서비스에서 isAdmin 검증 병행). */
    @GetMapping("/articles/trash")
    @ResponseBody
    @PreAuthorize("hasRole('SUB_ADMIN') or hasRole('ADMIN') or hasRole('MASTER')")
    public ResponseEntity<Page<ArticleUnifiedTrashDto>> listUnifiedTrash(
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        if (ms == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
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
    @ResponseBody
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

}
