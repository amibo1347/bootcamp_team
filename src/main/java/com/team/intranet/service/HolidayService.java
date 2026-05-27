package com.team.intranet.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.team.intranet.config.HolidayApiProperties;
import com.team.intranet.dto.HolidayDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 공공데이터포털 특일정보 API(SpcdeInfoService) 클라이언트 + 연도별 캐시.
 *  - 사용 엔드포인트: getRestDeInfo (공휴일 + 국경일 한 번에).
 *  - 인증키 미설정(holiday.api.service-key 비어있음) 이면 빈 리스트를 반환해
 *    프론트가 자체 lunar 계산으로 fallback 하도록 둔다.
 *  - 응답은 연도별 ConcurrentMap 에 캐시 — 같은 연도 재요청 시 외부 호출 안 한다.
 *  - 응답이 XML→JSON 변환되며 items.item 이 단일 객체로 올 수도, 배열로 올 수도 있다.
 *    여기서는 List/Map 양쪽 모두를 정규화한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HolidayService {

    /** 연도 단위 캐시 — 시작 후 같은 연도는 외부 호출 없이 반환. */
    private final Map<Integer, List<HolidayDto>> cache = new ConcurrentHashMap<>();

    private final HolidayApiProperties properties;
    private final RestTemplate restTemplate = new RestTemplate();

    public List<HolidayDto> findByYear(int year) {
        if (year < 1900 || year > 2100) return Collections.emptyList();
        String key = serviceKey();
        if (key == null || key.isBlank()) {
            // 인증키 없음 → 프론트가 자체 계산 fallback 을 쓰도록 빈 리스트.
            return Collections.emptyList();
        }
        return cache.computeIfAbsent(year, this::fetchYearFromApi);
    }

    private List<HolidayDto> fetchYearFromApi(int year) {
        String base = baseUrl();
        if (base == null || base.isBlank()) return Collections.emptyList();

        // numOfRows 를 충분히 크게 — 한 해 공휴일+국경일 합쳐 ~20건 수준이라 100 이면 안전.
        // UriComponentsBuilder 가 자동 URL 인코딩하므로 serviceKey 는 디코딩(원본)값을 그대로 사용.
        String url = UriComponentsBuilder.fromHttpUrl(base + "/getRestDeInfo")
                .queryParam("serviceKey", serviceKey())
                .queryParam("solYear", year)
                .queryParam("numOfRows", 100)
                .queryParam("_type", "json")
                .build(false) // 이미 인코딩된 형태로 두지 않고 우리가 넘긴 원본을 인코딩하도록 false
                .toUriString();

        try {
            ResponseEntity<Map> resp = restTemplate.getForEntity(url, Map.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                log.warn("[holiday-api] year={} status={} body=null", year, resp.getStatusCode());
                return Collections.emptyList();
            }
            return parseHolidayResponse(resp.getBody());
        } catch (RestClientException e) {
            log.warn("[holiday-api] year={} fetch failed: {}", year, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 응답 구조 예:
     *   response.body.items.item = [ { dateName, isHoliday, locdate, ... }, ... ]
     *   또는 단일 객체일 때: response.body.items.item = { dateName, ... }
     *   또는 데이터 0건일 때: items 가 "" 빈 문자열로 오기도 함.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<HolidayDto> parseHolidayResponse(Map body) {
        Object response = body.get("response");
        if (!(response instanceof Map respMap)) return Collections.emptyList();
        Object bodyObj = respMap.get("body");
        if (!(bodyObj instanceof Map bodyMap)) return Collections.emptyList();
        Object items = bodyMap.get("items");
        if (!(items instanceof Map itemsMap)) return Collections.emptyList();
        Object item = itemsMap.get("item");
        if (item == null) return Collections.emptyList();

        List<Map<String, Object>> rows;
        if (item instanceof List list) {
            rows = (List<Map<String, Object>>) list;
        } else if (item instanceof Map map) {
            rows = List.of((Map<String, Object>) map);
        } else {
            return Collections.emptyList();
        }

        List<HolidayDto> out = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            HolidayDto dto = toDto(row);
            if (dto != null) out.add(dto);
        }
        return out;
    }

    /** API 한 row → HolidayDto. locdate 가 yyyyMMdd 정수 또는 문자열. */
    private HolidayDto toDto(Map<String, Object> row) {
        Object locdate = row.get("locdate");
        Object name = row.get("dateName");
        if (locdate == null || name == null) return null;

        String iso = toIsoDate(String.valueOf(locdate));
        if (iso == null) return null;

        // isHoliday="Y" 는 공휴일(빨간 날). 국경일 중 isHoliday="N" 도 올 수 있는데
        // getRestDeInfo 응답은 대부분 Y 라 일단 전체를 PUBLIC 으로 둔다.
        return new HolidayDto(iso, String.valueOf(name).trim(), "PUBLIC");
    }

    /** "20260101" → "2026-01-01". 형식이 깨지면 null. */
    private String toIsoDate(String yyyymmdd) {
        String s = yyyymmdd == null ? "" : yyyymmdd.trim();
        if (s.length() != 8) return null;
        try {
            int y = Integer.parseInt(s.substring(0, 4));
            int m = Integer.parseInt(s.substring(4, 6));
            int d = Integer.parseInt(s.substring(6, 8));
            return LocalDate.of(y, m, d).toString();
        } catch (NumberFormatException | java.time.DateTimeException e) {
            return null;
        }
    }

    private String serviceKey() { return properties != null ? properties.serviceKey() : null; }
    private String baseUrl()    { return properties != null ? properties.baseUrl()    : null; }
}
