package com.team.intranet.controller.api;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.multipart.MultipartFile;

import com.team.intranet.entity.ApprovalAttachment;
import com.team.intranet.entity.Company;
import com.team.intranet.entity.Member;
import com.team.intranet.repository.ApprovalAttachmentRepository;
import com.team.intranet.repository.ApprovalLineRepository;
import com.team.intranet.repository.CompanyRepository;
import com.team.intranet.repository.MemberRepository;
import com.team.intranet.session.MemberSession;

import lombok.RequiredArgsConstructor;

/**
 * 전자결재 첨부파일 API. 게시글 첨부(AttachmentApiController) 와 동일한 흐름.
 *  - 업로드: 결재 제출 전에 미리 호출 → 받은 id 를 제출 payload 의 attachmentIds 로 같이 보냄.
 *  - 다운로드: 같은 회사 + (업로더 본인 또는 해당 결재의 기안자·결재자) 만 허용.
 */
@RestController
@RequestMapping("/api/approval-attachment")
@RequiredArgsConstructor
public class ApprovalAttachmentApiController {

    // 위험 확장자 블랙리스트 (실행 가능 파일 차단)
    private static final Set<String> BLOCKED_EXT =
            Set.of("exe", "bat", "sh", "cmd", "com", "msi", "scr", "jar");
    private static final long MAX_BYTES = 50L * 1024 * 1024; // 50MB

    private final ApprovalAttachmentRepository approvalAttachmentRepository;
    private final ApprovalLineRepository approvalLineRepository;
    private final MemberRepository memberRepository;
    private final CompanyRepository companyRepository;

    // 업로드 — 결재 제출 전에 미리 호출. 받은 id 를 제출 폼에 같이 보냄.
    @PostMapping
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) throws Exception {

        if (ms == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (file == null || file.isEmpty()) return ResponseEntity.badRequest().build();
        if (file.getSize() > MAX_BYTES) return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();

        String name = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
        if (BLOCKED_EXT.contains(ext)) return ResponseEntity.badRequest().build();

        Member uploader = memberRepository.findById(ms.getMemberId()).orElseThrow();
        Company company = companyRepository.findById(ms.getCompanyId()).orElseThrow();

        ApprovalAttachment att = ApprovalAttachment.builder()
                .data(file.getBytes())
                .originalFilename(name)
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .uploader(uploader)
                .company(company)
                .createdAt(LocalDateTime.now())
                .build();
        ApprovalAttachment saved = approvalAttachmentRepository.save(att);

        return ResponseEntity.ok(Map.of(
                "id", saved.getAttachmentId(),
                "filename", saved.getOriginalFilename(),
                "size", saved.getFileSize()
        ));
    }

    // 다운로드 — 다른 회사/없는 파일 차단 + 결재 관계자만 허용.
    @GetMapping("/{id}")
    public ResponseEntity<byte[]> download(
            @PathVariable Long id,
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {

        if (ms == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        ApprovalAttachment att = approvalAttachmentRepository.findById(id).orElse(null);
        if (att == null || att.getCompany() == null
                || !att.getCompany().getCompanyId().equals(ms.getCompanyId())) {
            return ResponseEntity.notFound().build();
        }
        if (!canAccess(att, ms.getMemberId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // 한글 파일명 인코딩 (RFC 5987)
        String encodedName = URLEncoder.encode(att.getOriginalFilename(), StandardCharsets.UTF_8)
                .replace("+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE,
                        att.getContentType() == null ? "application/octet-stream" : att.getContentType())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedName)
                .body(att.getData());
    }

    /**
     * 첨부 접근 권한:
     *  - 결재에 아직 연결 안 됨(approval == null) → 업로더 본인만.
     *  - 결재에 연결됨 → 그 결재의 기안자 또는 결재선에 포함된 결재자.
     */
    private boolean canAccess(ApprovalAttachment att, Long memberId) {
        if (att.getUploader() != null && att.getUploader().getMemberId().equals(memberId)) {
            return true;
        }
        if (att.getApproval() == null) {
            return false;
        }
        Long approvalId = att.getApproval().getApprovalId();
        if (att.getApproval().getDrafter() != null
                && att.getApproval().getDrafter().getMemberId().equals(memberId)) {
            return true;
        }
        return approvalLineRepository.findByApproval_ApprovalIdOrderByLevelAsc(approvalId).stream()
                .anyMatch(l -> l.getApprover() != null
                        && l.getApprover().getMemberId().equals(memberId));
    }
}
