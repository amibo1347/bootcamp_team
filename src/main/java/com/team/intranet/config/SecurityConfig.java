package com.team.intranet.config;

import com.team.intranet.config.LoginFailureHandler;
import com.team.intranet.config.LoginSuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration
@RequiredArgsConstructor 
@EnableMethodSecurity
public class SecurityConfig {

    private final LoginSuccessHandler loginSuccessHandler;
    private final LoginFailureHandler loginFailureHandler;
    private final MasterLoginSuccessHandler masterLoginSuccessHandler;
    private final MasterLoginFailureHandler masterLoginFailureHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }


    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("MASTER").implies("ADMIN") // MASTER는 ADMIN의 권한을 포함
                .role("ADMIN").implies("SUB_ADMIN") // ADMIN은 SUB_ADMIN의 권한을 포함
                .build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider(CustomUserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
    
        provider.setHideUserNotFoundExceptions(false); 
    
        return provider;
    }

    /**
     * MASTER 전용 보안 체인. /master/**, /api/master/** 에만 적용된다 (@Order(1) → 먼저 매칭).
     *  - 일반 회원 form-login(/member/login)과 완전히 분리된 /master/login 경로 사용.
     *  - 인증 주체는 MasterAdmin (MasterUserDetailsService). DaoAuthenticationProvider 는
     *    이 체인 안에서만 쓰이도록 지역 변수로 생성 (전역 AuthenticationProvider 빈과 충돌 방지).
     */
    @Bean
    @Order(1)
    public SecurityFilterChain masterSecurityFilterChain(HttpSecurity http,
            MasterUserDetailsService masterUserDetailsService,
            PasswordEncoder passwordEncoder) throws Exception {

        DaoAuthenticationProvider masterProvider = new DaoAuthenticationProvider();
        masterProvider.setUserDetailsService(masterUserDetailsService);
        masterProvider.setPasswordEncoder(passwordEncoder);
        masterProvider.setHideUserNotFoundExceptions(false);

        http
                .securityMatcher("/master/**", "/api/master/**")
                .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/master/**")
                )
                .authorizeHttpRequests(auth -> auth
                .requestMatchers("/master/login").permitAll()
                .anyRequest().hasRole("MASTER")
                )
                .formLogin(form -> form
                .loginPage("/master/login")
                .loginProcessingUrl("/master/login")
                .usernameParameter("loginId")
                .passwordParameter("password")
                .successHandler(masterLoginSuccessHandler)
                .failureHandler(masterLoginFailureHandler)
                .permitAll()
                )
                .logout(logout -> logout
                .logoutUrl("/master/logout")
                .logoutSuccessUrl("/master/login")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                )
                .authenticationProvider(masterProvider)
                // 2차 인증(TOTP) 게이트 — 비밀번호만 통과한 요청을 콘솔에서 막는다.
                .addFilterAfter(new MasterTotpGuardFilter(), AuthorizationFilter.class);

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationProvider authenticationProvider) throws Exception {
        http
                .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/**", "/company/**")
                )
                .authorizeHttpRequests(auth -> auth
                // 1. 정적 리소스는 무조건 통과 (가장 먼저)
                .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                // 2. 권한이 필요한 API/페이지 (까다로운 규칙을 먼저 선언)
                //  ※ /master/**, /api/master/** 는 masterSecurityFilterChain(@Order(1))이 처리.
                // 근태 관리: /admin/** prefix 지만 SUB_ADMIN 도 ATTENDANCE_MANAGEMENT 권한이 있으면 진입.
                //  - 실제 권한 검증은 컨트롤러 PreAuthorize + AttendanceService 내부에서 수행.
                .requestMatchers("/admin/attendance/**").authenticated()
                .requestMatchers("/api/admin/**", "/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/subAdmin/**", "/subAdmin/**").hasRole("SUB_ADMIN")
                // 3. 누구나 접근 가능한 페이지 및 API (나중에 선언)
                .requestMatchers("/error", "/index", "/calendar", "/member/**", "/api/member/**", "/api/company/**", "/images/**", "/api/uploads/**", "/uploads/**").permitAll()
                // 4. 그 외 모든 요청은 로그인 필요
                .anyRequest().authenticated()
                )
                .formLogin(form -> form
                .loginPage("/member/login")
                .loginProcessingUrl("/member/login") // 💡 HTML <form action="/member/login">과 일치
                .usernameParameter("loginId") // 💡 HTML <input name="loginId">와 일치
                .passwordParameter("password") // 💡 HTML <input name="password">와 일치
                .successHandler(loginSuccessHandler) // 💡 로그인 성공 시 세션 처리기
                .failureHandler(loginFailureHandler) // 💡 로그인 실패 시 에러 처리기
                .permitAll()
                )
                .authenticationProvider(authenticationProvider)
                .logout(logout -> logout
                .logoutUrl("/member/logout") // 💡 HTML에서 호출할 로그아웃 주소
                .logoutSuccessUrl("/member/login") // 💡 로그아웃 성공 후 이동할 페이지
                .invalidateHttpSession(true) // 💡 서버 세션 완전히 삭제 (중요!)
                .deleteCookies("JSESSIONID") // 💡 브라우저에 남은 세션 쿠키 삭제
                .permitAll()
                );

        return http.build();
    }
}
