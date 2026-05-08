package com.team.intranet.controller.api;

  import java.time.LocalDateTime;
  import java.util.Map;
  import java.util.Set;

  import org.springframework.http.HttpHeaders;
  import org.springframework.http.HttpStatus;
  import org.springframework.http.MediaType;
  import org.springframework.http.ResponseEntity;
  import org.springframework.web.bind.annotation.*;
  import org.springframework.web.multipart.MultipartFile;

  import com.team.intranet.entity.ArticleImage;
  import com.team.intranet.entity.Company;
  import com.team.intranet.entity.Member;
  import com.team.intranet.repository.ArticleImageRepository;
  import com.team.intranet.repository.CompanyRepository;
  import com.team.intranet.repository.MemberRepository;
  import com.team.intranet.session.MemberSession;

  import lombok.RequiredArgsConstructor;

  @RestController
  @RequestMapping("/api/article-image")
  @RequiredArgsConstructor
  public class UploadApiController {

      private static final Set<String> ALLOWED =
              Set.of("image/png", "image/jpeg", "image/gif", "image/webp");

      private final ArticleImageRepository imageRepository;
      private final MemberRepository memberRepository;
      private final CompanyRepository companyRepository;

      // 업로드 — JS에서 fetch('/api/article-image', FormData)
      @PostMapping
      public ResponseEntity<Map<String, String>> upload(
              @RequestParam("file") MultipartFile file,
              @SessionAttribute(name = "memberSession", required = false) MemberSession ms) throws Exception {

          if (ms == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
          if (file == null || file.isEmpty()) return ResponseEntity.badRequest().build();
          if (!ALLOWED.contains(file.getContentType())) return ResponseEntity.badRequest().build();
          if (file.getSize() > 10L * 1024 * 1024) return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();

          Member uploader = memberRepository.findById(ms.getMemberId()).orElseThrow();
          Company company = companyRepository.findById(ms.getCompanyId()).orElseThrow();

          ArticleImage image = ArticleImage.builder()
                  .data(file.getBytes())                // ← 프로필 사진과 똑같은 패턴
                  .contentType(file.getContentType())
                  .uploader(uploader)
                  .company(company)
                  .createdAt(LocalDateTime.now())
                  .build();
          ArticleImage saved = imageRepository.save(image);

          // 클라이언트가 <img src>에 박을 URL — 아래 GET이 같은 경로로 응답
          return ResponseEntity.ok(Map.of("url", "/api/article-image/" + saved.getImageId()));
      }

      // 조회 — <img src="/api/article-image/{id}">가 자동으로 호출
      @GetMapping("/{id}")
      public ResponseEntity<byte[]> get(@PathVariable Long id) {
          return imageRepository.findById(id)
                  .map(img -> ResponseEntity.ok()
                          .header(HttpHeaders.CONTENT_TYPE, img.getContentType())
                          .header(HttpHeaders.CACHE_CONTROL, "max-age=31536000, immutable")
                          .body(img.getData()))
                  .orElse(ResponseEntity.notFound().build());
      }
  }
