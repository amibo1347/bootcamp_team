package com.team.intranet.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // 1. 상수 정의
    /*
        400 : 문법 오류(요청이 이상함)
        401 : 인증 필요
        403 : 권한 거부
        404 : 찾을 수 없음
        405 : 요청 방식이 틀림
        409 : 충돌 오류
     */
    DEPT_NOT_FOUND(404, "해당 부서를 찾을 수 없습니다"),
    COMPANY_NOT_FOUND(404, "해당 기업을 찾을 수 없습니다"),
    MEMBER_NOT_FOUND(404, "해당 회원을 찾을 수 없습니다"),
    POSITION_NOT_FOUND(404, "해당 직급을 찾을 수 없습니다"),
    LOGIN_FAILED(401, "아이디 혹은 비밀번호가 틀렸습니다"),
    DUPLICATE_DEPT_NAME(409, "이미 존재하는 부서 이름입니다"),
    ALREADY_JOIN_MEMBER(400, "이미 승인된 회원입니다"),
    ACCESS_DENIED(403, "해당 데이터에 관한 권한이 없습니다"),
    WAITING_ACCEPT(403, "관리자의 승인을 기다리는 중입니다 "),
    NOT_ADMIN_ROLE(403, "관리자 권한이 없습니다"),
    NOT_BOARD_FOUND(404, "해당 게시판을 찾을 수 없습니다");

    // 2. 변수 정의
    private final int status;
    private final String message;
}
