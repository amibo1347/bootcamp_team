package com.team.intranet.exception;

import lombok.Getter;
import com.team.intranet.enums.ErrorCode;

@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    // 파라미터 생성자
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage()); 
        this.errorCode = errorCode;
    }
}