package com.team.intranet.controller.api;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team.intranet.service.CompanyService;

import lombok.RequiredArgsConstructor;

/**
 * 회사 공개 API — 로고 이미지 서빙.
 *  - 회원가입/로그인 화면(비로그인)에서도 로고를 보여줘야 하므로 /api/company/** 는 permitAll.
 *  - 회원 프로필 사진(/api/member/{id}/profileImg)과 동일한 BLOB 서빙 방식.
 */
@RestController
@RequestMapping("/api/company")
@RequiredArgsConstructor
public class CompanyApiController {

    private final CompanyService companyService;

    /** 회사 로고 이미지. 로고가 없거나 회사가 없으면 404. */
    @GetMapping("/{id}/logo")
    public ResponseEntity<byte[]> getLogo(@PathVariable Long id) {
        try {
            byte[] logo = companyService.getLogo(id);
            if (logo == null || logo.length == 0) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "image/png")
                    .body(logo);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
