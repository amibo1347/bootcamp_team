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
    INVALID_STATUS(400, "변경할 수 없는 상태값입니다"),
    MEMBER_NOT_OWNER(400, "본인이 아닙니다"),
    SUPERIOR_MEMBER_PROTECTED(403, "본인과 동등하거나 상위 직급 회원의 정보는 변경할 수 없습니다"),
    
    // 기업
    COMPANY_NOT_FOUND(404, "해당 기업을 찾을 수 없습니다"),
    COMPANY_ACCESS_DENIED(403, "해당 기업의 권한이 없습니다"),
    
    
    // 부서
    DEPT_NOT_FOUND(404, "해당 부서를 찾을 수 없습니다"),
    DUPLICATE_DEPT_NAME(409, "이미 존재하는 부서 이름입니다"),
    DEPT_IN_USE(409, "소속된 회원이 있어 부서를 삭제할 수 없습니다"),
    DEPT_NOT_MATCH(400, "소속된 부서가 아닙니다"),
    SYSTEM_PROTECTED_DEPT(409, "시스템 기본 부서는 삭제할 수 없습니다"),

    // 직급
    POSITION_NOT_FOUND(404, "해당 직급을 찾을 수 없습니다"),
    POSITION_IN_USE(409, "사용 중인 직급은 삭제할 수 없습니다"),
    SYSTEM_PROTECTED_POSITION(409, "시스템 기본 직급은 삭제할 수 없습니다"),
    SYSTEM_PROTECTED_POSITION_FIELD(409, "시스템 기본 직급은 레벨/역할을 변경할 수 없습니다"),
    
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
    NOT_ANONYMOUS_POST(400, "익명 게시글이 아닙니다"),

    // 댓글
    COMMENT_NOT_FOUND(404, "해당 댓글을 찾을 수 없습니다"),
    INVALID_COMMENT_DEPTH(400, "대댓글에는 답글을 달 수 없습니다"),

    // 카테고리
    CATEGORY_NOT_FOUND(404, "해당 카테고리를 찾을 수 없습니다"),
    DUPLICATE_CATEGORY_NAME(409, "이미 존재하는 카테고리입니다"),

    // 캘린더
    CALENDAR_ACCESS_DENIED(403, "일정에 관한 권한이 없습니다"),
    CALENDAR_NOT_OWNER(400, "일정의 작성자가 아닙니다"),
    CALENDAR_NOT_FOUND(404, "해당 일정을 찾을 수 없습니다"),

    // 결재
    FORM_TEMPLATE_NOT_FOUND(404, "해당 결재 양식을 찾을 수 없습니다"),
    FORM_TEMPLATE_INACTIVE(400, "비활성 상태인 결재 양식입니다"),
    DUPLICATE_FORM_CODE(409, "이미 사용 중인 양식 코드입니다. 시스템 양식이면 '복사하여 커스터마이즈'를 사용하세요"),
    
    APPROVAL_NOT_FOUND(404, "해당 결재 문서를 찾을 수 없습니다"),
    APPROVAL_ALREADY_PROCESSED(409, "이미 처리된 결재 문서입니다"),
    NOT_APPROVER(403, "해당 결재의 결재자가 아닙니다"),
    NOT_DRAFTER(403, "해당 결재의 신청자가 아닙니다"),
    APPROVER_CANNOT_BE_SELF(400, "본인을 결재자로 지정할 수 없습니다"),
    
    VACATION_REQUEST_NOT_FOUND(404, "해당 휴가 신청을 찾을 수 없습니다"),
    INVALID_VACATION_PERIOD(400, "휴가 시작일이 종료일보다 늦습니다"),

    // 알림
    ALERT_NOT_FOUND(404, "해당 알림을 찾을 수 없습니다"),
    ALERT_TOGGLE_NOT_ALLOWED(400, "이 게시판은 알림 설정을 변경할 수 없습니다"),

    // AI
    AI_SESSION_NOT_FOUND(404, "해당 AI 대화를 찾을 수 없습니다"),
    AI_RATE_LIMIT_EXCEEDED(429, "오늘 AI 호출 한도를 초과했습니다"),
    AI_PROVIDER_QUOTA_EXCEEDED(429, "오늘 AI 무료 사용량 한도에 도달했습니다. 내일 다시 시도해주세요."),
    AI_PROVIDER_ERROR(503, "AI 모델 호출에 실패했습니다");
    
    // 2. 변수 정의
    private final int status;
    private final String message;
}
