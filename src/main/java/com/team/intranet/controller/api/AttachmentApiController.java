package com.team.intranet.controller.api;

import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
  import java.nio.charset.StandardCharsets;
  import java.time.LocalDateTime;
  import java.util.List;
  import java.util.Map;

  import org.springframework.http.*;
  import org.springframework.web.bind.annotation.*;
  import org.springframework.web.multipart.MultipartFile;

  import com.team.intranet.entity.*;
  import com.team.intranet.repository.*;
  import com.team.intranet.session.MemberSession;
  import com.team.intranet.util.FileValidator;

  import lombok.RequiredArgsConstructor;

  @RestController
  @RequestMapping("/api/article-attachment")
  @RequiredArgsConstructor
  public class AttachmentApiController {

      private final AttachmentRepository attachmentRepository;
      private final MemberRepository memberRepository;
      private final CompanyRepository companyRepository;
      private final FileValidator fileValidator;

      // 업로드 — 글 저장 전에 미리 호출. 받은 id를 글 폼에 같이 보냄
      @PostMapping
      public ResponseEntity<Map<String, Object>> upload(
              @RequestParam("file") MultipartFile file,
              @SessionAttribute(name = "memberSession", required = false) MemberSession ms) throws Exception {

          if (ms == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
          fileValidator.validateAttachment(file);

          String name = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();

          Member uploader = memberRepository.findById(ms.getMemberId()).orElseThrow();
          Company company = companyRepository.findById(ms.getCompanyId()).orElseThrow();

          ArticleAttachment att = ArticleAttachment.builder()
                  .data(file.getBytes())
                  .originalFilename(name)
                  .contentType(file.getContentType())
                  .fileSize(file.getSize())
                  .uploader(uploader)
                  .company(company)
                  .createdAt(LocalDateTime.now())
                  .build();
          ArticleAttachment saved = attachmentRepository.save(att);

          return ResponseEntity.ok(Map.of(
                  "id", saved.getAttachmentId(),
                  "filename", saved.getOriginalFilename(),
                  "size", saved.getFileSize()
          ));
      }

      // 다운로드 — 다른 회사/없는 파일 차단
      @GetMapping("/{id}")
      public ResponseEntity<byte[]> download(
              @PathVariable Long id,
              @SessionAttribute(name = "memberSession", required = false) MemberSession ms) throws Exception {

          if (ms == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

          ArticleAttachment att = attachmentRepository.findById(id).orElse(null);
          if (att == null || !att.getCompany().getCompanyId().equals(ms.getCompanyId())) {
              return ResponseEntity.notFound().build();
          }

          if (att.getArticle() != null && att.getArticle().isDeleted()) {
          return ResponseEntity.notFound().build();
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

      // 글 상세 페이지에서 호출 — 해당 글의 첨부파일 목록(메타만)
      @GetMapping
      public ResponseEntity<List<Map<String, Object>>> listByArticle(
              @RequestParam Long articleId,
              @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {

          if (ms == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

          List<Map<String, Object>> result = attachmentRepository.findByArticle_ArticleId(articleId).stream()
                  .filter(a -> a.getCompany().getCompanyId().equals(ms.getCompanyId()))
                  .map(a -> Map.<String, Object>of(
                          "id", a.getAttachmentId(),
                          "filename", a.getOriginalFilename(),
                          "size", a.getFileSize(),
                          "downloadUrl", "/api/article-attachment/" + a.getAttachmentId()
                  ))
                  .toList();
          return ResponseEntity.ok(result);
      }

      @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(
          @PathVariable Long id,
          @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {

      if (ms == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

      ArticleAttachment att = attachmentRepository.findById(id).orElse(null);
      if (att == null) return ResponseEntity.notFound().build();

      // 회사 검증 (멀티 테넌시)
      if (!att.getCompany().getCompanyId().equals(ms.getCompanyId())) {
          return ResponseEntity.notFound().build();
      }

      // 권한 검증
      boolean allowed = (att.getArticle() == null)
          // 아직 글에 연결 안 된 첨부 → 본인이 올린 것만 삭제
          ? att.getUploader().getMemberId().equals(ms.getMemberId())
          // 글에 연결된 첨부 → 그 글의 작성자만 삭제
          : att.getArticle().getAuthor().getMemberId().equals(ms.getMemberId());

      if (!allowed) {
          return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
      }

      attachmentRepository.delete(att);
      return ResponseEntity.noContent().build();
    }

}
