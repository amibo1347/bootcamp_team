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

    // 인증/인가
    LOGIN_FAILED(401, "아이디 혹은 비밀번호가 틀렸습니다"),
    ACCESS_DENIED(403, "해당 데이터에 관한 권한이 없습니다"),
    WAITING_ACCEPT(403, "관리자의 승인을 기다리는 중입니다 "),
    NOT_ADMIN_ROLE(403, "관리자 권한이 없습니다"),
    DUPLICATE_LOGIN_ID(409, "이미 사용 중인 아이디입니다"),
    PASSWORD_MISMATCH(400, "비밀번호가 일치하지 않습니다"),
    INVALID_COMPANY_CODE(400, "유효하지 않은 회사 코드입니다"),
    NO_AUTHORITY(403, "사용 권한이 없습니다"),
    
    // 회원
    MEMBER_NOT_FOUND(404, "해당 회원을 찾을 수 없습니다"),
    ALREADY_JOIN_MEMBER(400, "이미 승인된 회원입니다"),
    MEMBER_REJECTED(403, "가입이 거절된 회원입니다"),
    MEMBER_LEFT(403, "퇴사한 회원입니다"),
    MEMBER_NOT_WAITING(400, "승인 대기 상태가 아닌 회원입니다"),
    
    // 기업
    COMPANY_NOT_FOUND(404, "해당 기업을 찾을 수 없습니다"),
    
    
    // 부서
    DEPT_NOT_FOUND(404, "해당 부서를 찾을 수 없습니다"),
    DUPLICATE_DEPT_NAME(409, "이미 존재하는 부서 이름입니다"),
    DEPT_IN_USE(409, "소속된 회원이 있어 부서를 삭제할 수 없습니다"),
    
    // 직급
    POSITION_NOT_FOUND(404, "해당 직급을 찾을 수 없습니다"),
    DUPLICATE_POSITION_LEVEL(409, "이미 사용 중인 직급 레벨입니다"),
    POSITION_IN_USE(409, "사용 중인 직급은 삭제할 수 없습니다"),
    
    // 게시판
    NO_BOARD_READ_PERMISSION(403, "게시판 조회 권한이 없습니다"),
    NO_BOARD_WRITE_PERMISSION(403, "게시판 쓰기 권한이 없습니다"),
    NO_BOARD_COMMENT_PERMISSION(403, "댓글 작성 권한이 없습니다"),
    BOARD_INACTIVE(400, "비활성 상태인 게시판입니다"),
    DUPLICATE_BOARD_NAME(409, "이미 존재하는 게시판 이름입니다"),
    BOARD_NOT_FOUND(404, "해당 게시판을 찾을 수 없습니다"),
    
    // 게시글
    FILE_TOO_LARGE(403, "파일이 너무 큽니다"),
    INVALID_FILE_TYPE(403, "파일 형식이 맞지 않습니다"),
    INVALID_INPUT(403, "파일이 깨졌습니다"),
    ARTICLE_NOT_FOUND(404, "해당 게시글을 찾을 수 없습니다"),
    ATTACHMENT_NOT_FOUND(404, "첨부파일을 찾을 수 없습니다"),
    ALEADY_DELETE_ARTICLE(409, "이미 삭제된 게시글입니다"),
    NOT_ANONYMOUS_POST(400, "익명 게시글이 아닙니다");
    
    // 2. 변수 정의
    private final int status;
    private final String message;
}
