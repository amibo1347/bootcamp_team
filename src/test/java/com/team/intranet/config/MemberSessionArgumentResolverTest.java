package com.team.intranet.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;

import com.team.intranet.enums.ErrorCode;
import com.team.intranet.enums.member.Role;
import com.team.intranet.exception.BusinessException;
import com.team.intranet.session.MemberSession;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * MemberSessionArgumentResolver 단위 테스트 — Mock 으로 NativeWebRequest 만들어 4가지 시나리오 검증.
 *  - 세션 attribute 있음 → MemberSession 주입
 *  - 세션 attribute 없음 + required=true → BusinessException(AUTHENTICATION_REQUIRED)
 *  - 세션 attribute 없음 + required=false → null
 *  - HttpSession 자체가 null + required=false → null
 *
 *  + supportsParameter 가 MemberSession 타입에만 true, 다른 타입엔 false
 */
class MemberSessionArgumentResolverTest {

    private MemberSessionArgumentResolver resolver;
    private NativeWebRequest webRequest;
    private HttpServletRequest httpRequest;

    @BeforeEach
    void setUp() {
        resolver = new MemberSessionArgumentResolver();
        webRequest = mock(NativeWebRequest.class);
        httpRequest = mock(HttpServletRequest.class);
        when(webRequest.getNativeRequest(HttpServletRequest.class)).thenReturn(httpRequest);
    }

    // ---- supportsParameter ----

    @Test
    @DisplayName("MemberSession + @AuthenticatedMember 면 support")
    void supports_authMemberOnMemberSession() throws Exception {
        MethodParameter param = paramOf("withAnnotation", MemberSession.class);
        assertThat(resolver.supportsParameter(param)).isTrue();
    }

    @Test
    @DisplayName("@AuthenticatedMember 없으면 support 안 함")
    void supports_noAnnotation_false() throws Exception {
        MethodParameter param = paramOf("noAnnotation", MemberSession.class);
        assertThat(resolver.supportsParameter(param)).isFalse();
    }

    @Test
    @DisplayName("@AuthenticatedMember 있어도 타입이 MemberSession 아니면 false")
    void supports_wrongType_false() throws Exception {
        MethodParameter param = paramOf("wrongType", String.class);
        assertThat(resolver.supportsParameter(param)).isFalse();
    }

    // ---- resolveArgument ----

    @Nested
    @DisplayName("resolveArgument")
    class Resolve {

        @Test
        @DisplayName("세션 attribute 가 MemberSession 이면 그대로 주입")
        void returnsSession_whenAttributePresent() throws Exception {
            MemberSession expected = stubSession();
            HttpSession session = mock(HttpSession.class);
            when(httpRequest.getSession(false)).thenReturn(session);
            when(session.getAttribute("memberSession")).thenReturn(expected);

            Object result = resolver.resolveArgument(paramOf("withAnnotation", MemberSession.class),
                    null, webRequest, null);

            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("세션 없음 + required=true → AUTHENTICATION_REQUIRED")
        void throws_whenRequiredButMissing() throws Exception {
            when(httpRequest.getSession(false)).thenReturn(null);

            assertThatThrownBy(() ->
                    resolver.resolveArgument(paramOf("withAnnotation", MemberSession.class),
                            null, webRequest, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED));
        }

        @Test
        @DisplayName("세션 attribute 없음 + required=true → AUTHENTICATION_REQUIRED")
        void throws_whenSessionPresentButAttributeMissing() throws Exception {
            HttpSession session = mock(HttpSession.class);
            when(httpRequest.getSession(false)).thenReturn(session);
            when(session.getAttribute("memberSession")).thenReturn(null);

            assertThatThrownBy(() ->
                    resolver.resolveArgument(paramOf("withAnnotation", MemberSession.class),
                            null, webRequest, null))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("세션 없음 + required=false → null 주입")
        void returnsNull_whenOptional() throws Exception {
            when(httpRequest.getSession(false)).thenReturn(null);

            Object result = resolver.resolveArgument(paramOf("optional", MemberSession.class),
                    null, webRequest, null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("세션 attribute 가 잘못된 타입이면 required=true 일 때 throw")
        void throws_whenWrongAttributeType() throws Exception {
            HttpSession session = mock(HttpSession.class);
            when(httpRequest.getSession(false)).thenReturn(session);
            when(session.getAttribute("memberSession")).thenReturn("not a MemberSession");

            assertThatThrownBy(() ->
                    resolver.resolveArgument(paramOf("withAnnotation", MemberSession.class),
                            null, webRequest, null))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // ---- 테스트 보조 ----

    /** Reflection 으로 아래 더미 메서드의 파라미터 추출. */
    private MethodParameter paramOf(String methodName, Class<?> paramType) throws NoSuchMethodException {
        Method m = DummyController.class.getDeclaredMethod(methodName, paramType);
        return new MethodParameter(m, 0);
    }

    private MemberSession stubSession() {
        return new MemberSession(
                1L, "loginId", "name", "email", null, null,
                Role.USER, 1L, "company", "사원", 1L, 1, 1L, "dept", false,
                java.util.EnumSet.noneOf(com.team.intranet.enums.member.SubAdminPermission.class)
        );
    }

    /** 어노테이션 조합별 더미 시그니처 보관용. */
    @SuppressWarnings("unused")
    private static class DummyController {
        void withAnnotation(@AuthenticatedMember MemberSession ms) {}
        void noAnnotation(MemberSession ms) {}
        void wrongType(@AuthenticatedMember String s) {}
        void optional(@AuthenticatedMember(required = false) MemberSession ms) {}
    }

    /** @AuthenticatedMember 의 기본 required=true 값 검증. */
    @Test
    @DisplayName("@AuthenticatedMember 기본 required=true")
    void defaultRequiredIsTrue() throws Exception {
        Method m = DummyController.class.getDeclaredMethod("withAnnotation", MemberSession.class);
        Annotation[] annotations = m.getParameterAnnotations()[0];
        AuthenticatedMember a = (AuthenticatedMember) annotations[0];
        assertThat(a.required()).isTrue();
    }
}
