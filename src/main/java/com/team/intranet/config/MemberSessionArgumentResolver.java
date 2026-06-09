package com.team.intranet.config;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.team.intranet.enums.ErrorCode;
import com.team.intranet.exception.BusinessException;
import com.team.intranet.session.MemberSession;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * @AuthenticatedMember 처리기. HttpSession 의 "memberSession" attribute 를 꺼내 컨트롤러 파라미터로 주입.
 *  - required=true (기본): 세션 없거나 attribute 없으면 BusinessException(AUTHENTICATION_REQUIRED) → 401 자동 응답.
 *  - required=false: 비로그인 시 null 주입.
 *
 *  컨트롤러마다 반복되던 @SessionAttribute + if(ms == null) UNAUTHORIZED 가드를 한 곳에 집약.
 */
@Component
public class MemberSessionArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String SESSION_ATTR = "memberSession";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AuthenticatedMember.class)
                && MemberSession.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        AuthenticatedMember annotation = parameter.getParameterAnnotation(AuthenticatedMember.class);
        boolean required = annotation == null || annotation.required();

        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        HttpSession session = request != null ? request.getSession(false) : null;
        Object value = session != null ? session.getAttribute(SESSION_ATTR) : null;

        if (value instanceof MemberSession ms) {
            return ms;
        }
        if (required) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
        return null;
    }
}
