package com.team.intranet.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 파라미터에 붙여 현재 로그인 회원의 MemberSession 을 자동 주입.
 *  - MemberSessionArgumentResolver 가 처리.
 *  - 기존 @SessionAttribute("memberSession") + if(ms == null) UNAUTHORIZED 가드 반복을 한 곳에 모은다.
 *
 * 사용:
 *   public ResponseEntity<?> handler(@AuthenticatedMember MemberSession ms) { ... }
 *
 * required=false 로 비로그인 허용 분기에도 사용 가능 (이 경우 ms 가 null 일 수 있음).
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthenticatedMember {
    /** false 면 비로그인 시 null 주입, true(기본) 면 401 자동 응답. */
    boolean required() default true;
}
