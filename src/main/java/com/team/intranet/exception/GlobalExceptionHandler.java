package com.team.intranet.exception;

import com.team.intranet.enums.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 비즈니스 예외 처리
     * - 사용자에게 보여줄 수 있는 에러 (예: "이미 존재하는 부서입니다")
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();

        log.warn("BusinessException: {} - {}", errorCode.name(), errorCode.getMessage());

        Map<String, Object> body = Map.of(
            "errorCode", errorCode.name(),
            "message", errorCode.getMessage()
        );

        return ResponseEntity
            .status(errorCode.getStatus())
            .body(body);
    }

    /**
     * 그 외 예상하지 못한 예외 처리
     * - 사용자에게는 일반적인 메시지만 노출
     * - 상세 내용은 서버 로그에만 남김 (보안상 중요)
     */

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFound(NoResourceFoundException e) {
        // 로그도 안 남김 (너무 자주 발생)
        return ResponseEntity.notFound().build();
    }

    /**
     * SSE/비동기 응답에서 클라이언트가 연결 끊은 경우 → 응답 못 씀.
     * 보통 페이지 이동/새로고침으로 EventSource 끊겼을 때 발생. 정상 상황이므로 조용히 무시.
     */
    @ExceptionHandler({ AsyncRequestNotUsableException.class, ClientAbortException.class })
    public void handleClientDisconnect(Exception e) {
        // 응답 자체가 안 가는 상황. body 작성 시도하면 또 예외. void 반환으로 종료.
        log.debug("[ClientDisconnect] {}: {}", e.getClass().getSimpleName(), e.getMessage());
    }

    /**
     * SSE heartbeat 이 비동기 dispatch 경로로 던지는 IOException 도 client-disconnect 의 변형.
     *  - 메시지 패턴(영문 "Broken pipe"/"Connection reset"/"aborted"/"closed"/"Connection refused" 등
     *    + 한국어 "중단" — 윈도우 로컬화 메시지)으로만 좁혀 잡고, 그 외는 일반 핸들러로 흘려보낸다.
     *  - ClientAbortException 이 IOException 의 서브클래스라 이 핸들러는 그 외 IOException 만 처리.
     */
    @ExceptionHandler(IOException.class)
    public ResponseEntity<Map<String, Object>> handleIOException(IOException e) throws IOException {
        String msg = e.getMessage() != null ? e.getMessage() : "";
        boolean clientDisconnect = msg.contains("Broken pipe")
                || msg.contains("Connection reset")
                || msg.contains("aborted")
                || msg.contains("closed")
                || msg.contains("중단");          // 윈도우 한국어 로컬 메시지
        if (clientDisconnect) {
            log.debug("[ClientDisconnect-IO] {}", msg);
            return null;
        }
        // 그 외 진짜 I/O 오류는 일반 처리로 위임.
        throw e;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        log.error("Unhandled exception", e);

        Map<String, Object> body = Map.of(
            "errorCode", "INTERNAL_SERVER_ERROR",
            "message", "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요."
        );

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(body);
    }
}
