package com.team.intranet.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 채팅 실시간 이벤트 브로커 (Server-Sent Events).
 *  - 회원별로 다중 emitter 보관: 같은 사람이 여러 탭/장치로 동시 접속 가능.
 *  - 메시지 publish 는 상대방에게만. 발송자 본인은 sendMessage 응답으로 이미 받음.
 *  - 끊긴 emitter 는 onCompletion / onTimeout / onError 콜백에서 자동 제거.
 */
@Service
public class ChatSseService {

    /** SSE 연결 유지 시간. 브라우저 EventSource 는 timeout 시 자동 재연결한다. */
    private static final long TIMEOUT_MS = 30L * 60 * 1000;

    private final Map<Long, List<SseEmitter>> emittersByMember = new ConcurrentHashMap<>();

    /** memberId 로 구독. 호출자는 반환된 emitter 를 컨트롤러에서 그대로 리턴하면 됨. */
    public SseEmitter subscribe(Long memberId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        List<SseEmitter> list = emittersByMember.computeIfAbsent(memberId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);

        Runnable cleanup = () -> {
            list.remove(emitter);
            if (list.isEmpty()) emittersByMember.remove(memberId, list);
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        // 첫 핸드셰이크 — 연결 확인용. 실패해도 무시 (즉시 끊긴 케이스).
        try {
            emitter.send(SseEmitter.event().name("ready").data("ok"));
        } catch (IOException ignored) {
            cleanup.run();
        }
        return emitter;
    }

    /** SSE 이벤트명. 브라우저 기본 "message" 채널과 겹치지 않게 별도 이름 사용. */
    public static final String EVENT_CHAT_MESSAGE = "chat-message";

    /** toMemberId 의 모든 활성 emitter 에 채팅 메시지 이벤트 전송. */
    public void publishMessage(Long toMemberId, Map<String, Object> payload) {
        List<SseEmitter> list = emittersByMember.get(toMemberId);
        if (list == null || list.isEmpty()) return;
        for (SseEmitter e : list) {
            try {
                e.send(SseEmitter.event()
                    .name(EVENT_CHAT_MESSAGE)
                    .data(payload, MediaType.APPLICATION_JSON));
            } catch (Exception ex) {
                try { e.completeWithError(ex); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * 좀비 emitter 정리용 heartbeat — 15초마다 모든 emitter 에 ping 이벤트 발송.
     * 끊긴 emitter 는 send 시 예외 → cleanup 콜백으로 자동 제거.
     * 클라이언트는 이 ping 을 무시하면 됨 (event name="ping").
     */
    @Scheduled(fixedDelay = 15_000)
    public void heartbeat() {
        if (emittersByMember.isEmpty()) return;
        emittersByMember.forEach((memberId, list) -> {
            for (SseEmitter e : list) {
                try {
                    e.send(SseEmitter.event().name("ping").data("hb"));
                } catch (Exception ex) {
                    // 좀비 — completeWithError → onError 콜백 → cleanup 자동
                    try { e.completeWithError(ex); } catch (Exception ignored) {}
                }
            }
        });
    }
}
