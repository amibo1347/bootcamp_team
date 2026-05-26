package com.team.intranet.config;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 기업 코드 인증(/api/member/company/verify) 무차별 대입 방지 — IP 단위 sliding window 카운터.
 *  - 10분 윈도우 / 최대 5회 실패 → 초과 시 차단.
 *  - 성공 시 해당 IP 카운터 리셋. NAT 뒤 정상 사용자가 통과하면 다음 사용자도 다시 5회.
 *  - 외부 라이브러리 없이 ConcurrentHashMap + @Scheduled cleanup(1시간) 으로 메모리 누수 방지.
 *  - 단일 인스턴스 전제. 멀티 인스턴스로 가게 되면 Redis 등 공유 저장소로 이관 필요.
 */
@Component
public class VerifyCompanyRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(10);
    private static final int MAX_ATTEMPTS = 5;

    private static class Counter {
        int attempts;
        Instant windowStart;
        Counter(Instant now) { this.attempts = 0; this.windowStart = now; }
    }

    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();

    /** 시도 허용 여부 — false 면 429 응답. 결과 기록은 markFailure/markSuccess 에서 한다. */
    public boolean isAllowed(String ip) {
        Counter c = counters.get(ip);
        if (c == null) return true;
        if (isExpired(c)) return true;
        return c.attempts < MAX_ATTEMPTS;
    }

    /** 실패 1회 기록 — 윈도우 없거나 만료되었으면 새 윈도우 시작. */
    public synchronized void markFailure(String ip) {
        Instant now = Instant.now();
        Counter c = counters.get(ip);
        if (c == null || isExpired(c)) {
            c = new Counter(now);
            counters.put(ip, c);
        }
        c.attempts++;
    }

    /** 성공 시 카운터 리셋 — 다음 시도가 다시 0/5 부터. */
    public void markSuccess(String ip) {
        counters.remove(ip);
    }

    private boolean isExpired(Counter c) {
        return Duration.between(c.windowStart, Instant.now()).compareTo(WINDOW) > 0;
    }

    /** 만료된 엔트리 청소 — 1시간 마다. 메모리 누수 방지. */
    @Scheduled(fixedRate = 60 * 60 * 1000)
    public void cleanup() {
        Iterator<Map.Entry<String, Counter>> it = counters.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Counter> e = it.next();
            if (isExpired(e.getValue())) it.remove();
        }
    }
}
