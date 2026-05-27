package com.team.intranet.controller.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.team.intranet.dto.HolidayDto;
import com.team.intranet.service.HolidayService;

import lombok.RequiredArgsConstructor;

/**
 * 캘린더 공휴일 조회 API — 공공데이터포털 특일정보 API 결과를 연도 단위로 응답.
 *  - 인증 필요: 캘린더 페이지가 로그인 후 진입이라 자연스럽게 SecurityConfig.anyRequest().authenticated() 에 포함.
 *  - 인증키 미설정이면 빈 리스트 반환 → 프론트는 자체 lunar 계산 fallback 사용.
 */
@RestController
@RequestMapping("/api/holidays")
@RequiredArgsConstructor
public class HolidayApiController {

    private final HolidayService holidayService;

    @GetMapping
    public ResponseEntity<List<HolidayDto>> list(@RequestParam int year) {
        return ResponseEntity.ok(holidayService.findByYear(year));
    }
}
