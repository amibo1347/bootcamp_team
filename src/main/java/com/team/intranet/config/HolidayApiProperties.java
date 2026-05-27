package com.team.intranet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 공공데이터포털 특일정보 API 설정.
 *  - serviceKey: data.go.kr 발급 인증키(디코딩 키). 빈 값이면 백엔드 휴일 API 는 빈 배열을 반환하고,
 *    프론트가 자체 lunar 계산으로 fallback 한다.
 *  - baseUrl: SpcdeInfoService 의 베이스. 끝에 슬래시 없음.
 */
@ConfigurationProperties(prefix = "holiday.api")
public record HolidayApiProperties(String serviceKey, String baseUrl) {
}
