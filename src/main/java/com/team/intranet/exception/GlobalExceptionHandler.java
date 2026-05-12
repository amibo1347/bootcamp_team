package com.team.intranet.exception;

import com.team.intranet.enums.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
     * @ModelAttribute 등 폼 바인딩 실패 (빈 문자열 → Long, 잘못된 날짜 형식 등)
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Map<String, Object>> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + (err.getDefaultMessage() != null ? err.getDefaultMessage() : "값이 올바르지 않습니다"))
                .orElse("요청 형식이 올바르지 않습니다.");
        log.warn("BindException: {}", message);
        Map<String, Object> body = Map.of(
                "errorCode", "BAD_REQUEST",
                "message", message
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
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