package com.team.intranet.enums.member;

public enum MemberType {
    JOIN_SUCCESS,           // 가입 성공
    ALREADY_MEMBER,          // 이미 회원이 존재
    NOT_MATCH_PASSWORD,     // 비밀번호가 일치하지 않음
    NOT_COMPANY             // 존재하지 않는 기업
}
