package com.team.intranet.config;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 기업 코드 인증(/api/member/company/verify) 무차별 대입 방지 — IP 단위 sliding window 카운터.
 *  - 10분 윈도우 / 최대 5회 실패 → 초과 시 차단.
 *  - 성공 시 해당 IP 카운터 리셋. NAT 뒤 정상 사용자가 통과하면 다음 사용자도 다시 5회.
 *
 *  동시성:
 *   - ConcurrentHashMap.compute() 로 윈도우 만료 검사 + 카운터 증가를 단일 atomic 연산으로 묶는다.
 *   - attempts 는 AtomicInteger — compute 블록 밖에서 안전하게 읽기 가능.
 *   - 단일 인스턴스 전제. 멀티 인스턴스로 가게 되면 Redis 등 공유 저장소로 이관 필요.
 */
@Component
public class VerifyCompanyRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(10);
    private static final int MAX_ATTEMPTS = 5;

    private static final class Counter {
        final AtomicInteger attempts;
        final Instant windowStart;
        Counter(Instant now) {
            this.attempts = new AtomicInteger();
            this.windowStart = now;
        }
    }

    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();

    /** 시도 허용 여부 — false 면 429 응답. 만료된 윈도우는 통과로 처리. */
    public boolean isAllowed(String ip) {
        Counter c = counters.get(ip);
        if (c == null || isExpired(c, Instant.now())) return true;
        return c.attempts.get() < MAX_ATTEMPTS;
    }

    /** 실패 1회 기록 — 윈도우 만료 검사 + 카운터 증가가 단일 atomic 연산. */
    public void markFailure(String ip) {
        Instant now = Instant.now();
        counters.compute(ip, (k, existing) -> {
            Counter target = (existing == null || isExpired(existing, now))
                    ? new Counter(now)
                    : existing;
            target.attempts.incrementAndGet();
            return target;
        });
    }

    /** 성공 시 카운터 리셋 — 다음 시도가 다시 0/5 부터. */
    public void markSuccess(String ip) {
        counters.remove(ip);
    }

    private boolean isExpired(Counter c, Instant now) {
        return Duration.between(c.windowStart, now).compareTo(WINDOW) > 0;
    }

    /** 만료된 엔트리 청소 — 1시간 마다. 메모리 누수 방지. */
    @Scheduled(fixedRate = 60 * 60 * 1000)
    public void cleanup() {
        Instant now = Instant.now();
        counters.entrySet().removeIf(e -> isExpired(e.getValue(), now));
    }
}
