package com.team.intranet.config;

import com.team.intranet.config.LoginFailureHandler;
import com.team.intranet.config.LoginSuccessHandler;
import com.team.intranet.repository.CompanyRepository;
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
    private final MemberLogoutSuccessHandler memberLogoutSuccessHandler;
    private final MasterLoginSuccessHandler masterLoginSuccessHandler;
    private final MasterLoginFailureHandler masterLoginFailureHandler;
    private final CompanyAwareAuthenticationEntryPoint companyAwareAuthenticationEntryPoint;

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

    // 일반 회원 인증은 MemberAuthenticationProvider(회사+loginId 복합키)가 담당한다.
    // → 전역 DaoAuthenticationProvider 빈은 더 이상 두지 않는다. MASTER 체인은 자체 provider 사용.

    /**
     * MASTER 전용 보안 체인. /master/**, /api/master/** 에만 적용된다 (@Order(1) → 먼저 매칭).
     *  - 일반 회원 form-login(/{companyDomain}/login)과 완전히 분리된 /master/login 경로 사용.
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
                .deleteCookies("TEAM_SESSIONID")
                )
                .authenticationProvider(masterProvider)
                // 2차 인증(TOTP) 게이트 — 비밀번호만 통과한 요청을 콘솔에서 막는다.
                .addFilterAfter(new MasterTotpGuardFilter(), AuthorizationFilter.class);

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            MemberAuthenticationProvider memberAuthenticationProvider,
            CompanyRepository companyRepository,
            com.team.intranet.service.SystemMaintenanceService systemMaintenanceService) throws Exception {
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
                // 조직도 구성·승인 대기·부서/직급 관리: URL 은 /admin/** 이지만 SUB_ADMIN 도 진입한다.
                //  - 컨트롤러(@PreAuthorize SUB_ADMIN OR ADMIN) + 서비스(권한별 분기) 에서 세부 검증.
                //  - /admin/** 의 ADMIN 전용 매처보다 먼저 선언해야 hasAnyRole 이 우선 적용된다.
                .requestMatchers(
                    "/admin/waitingList", "/admin/memberList",
                    "/admin/dept/**", "/admin/position/**",
                    "/api/admin/dept/**", "/api/admin/position/**"
                ).hasAnyRole("SUB_ADMIN", "ADMIN")
                .requestMatchers("/api/admin/**", "/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/subAdmin/**", "/subAdmin/**").hasRole("SUB_ADMIN")
                // 3. 누구나 접근 가능한 페이지 및 API (나중에 선언)
                //  ※ /company-login = 회사 선택 랜딩, /*/login·/*/signup = 회사별 로그인/회원가입
                //  ※ /api/member/** 통째 permitAll 금지 — 회원가입 단계에서 실제로 비로그인이 필요한 것만 명시.
                //     check-id : 사번/아이디 중복 검사 (회원가입 폼)
                //     company/verify : 기업 코드 인증 (회원가입 1단계)
                //     나머지(me/**, {id}/profileImg)는 로그인 후 호출만 존재 → authenticated 로 떨어진다.
                .requestMatchers("/api/member/check-id", "/api/member/company/verify").permitAll()
                .requestMatchers("/error", "/index", "/company-login", "/*/login", "/*/signup", "/member/logout", "/api/company/**").permitAll()
                // 시스템 점검 안내 페이지 + 점검 상태 폴링 API — 점검 중에도, 비로그인도 접근 가능해야 함.
                .requestMatchers("/maintenance", "/api/system-maintenance/**").permitAll()
                // 4. 그 외 모든 요청은 로그인 필요
                .anyRequest().authenticated()
                )
                .formLogin(form -> form
                .loginPage("/company-login") // 💡 비로그인 진입 시 보내는 회사 선택 랜딩
                .loginProcessingUrl("/*/login") // 💡 회사 로그인 폼이 POST 하는 경로 /{companyDomain}/login
                .usernameParameter("loginId") // 💡 HTML <input name="loginId">와 일치
                .passwordParameter("password") // 💡 HTML <input name="password">와 일치
                // 💡 로그인 폼 hidden 필드 companyId 를 인증 컨텍스트(details)에 싣는다.
                .authenticationDetailsSource(new CompanyAwareAuthenticationDetailsSource())
                .successHandler(loginSuccessHandler) // 💡 로그인 성공 시 세션 처리기
                .failureHandler(loginFailureHandler) // 💡 로그인 실패 시 에러 처리기
                .permitAll()
                )
                .authenticationProvider(memberAuthenticationProvider)
                // 💡 비인증 진입 시 회사별 로그인 페이지로 보냄 (lastCompanyDomain 쿠키 기반).
                //    SecurityConfig 의 loginPage("/company-login") 만으로는 회사 정보를 알 수 없어
                //    자동 로그아웃 후 회사 선택 랜딩으로 떨어지는 문제를 해결.
                .exceptionHandling(ex -> ex.authenticationEntryPoint(companyAwareAuthenticationEntryPoint))
                .logout(logout -> logout
                .logoutUrl("/member/logout") // 💡 HTML에서 호출할 로그아웃 주소
                // 💡 회원이 속한 회사의 로그인 페이지(/{companyDomain}/login) 로 보낸다. 핸들러 내부에서
                //    인증 정보를 못 읽으면 /company-login 으로 폴백한다.
                .logoutSuccessHandler(memberLogoutSuccessHandler)
                .invalidateHttpSession(true) // 💡 서버 세션 완전히 삭제 (중요!)
                .deleteCookies("TEAM_SESSIONID") // 💡 브라우저에 남은 세션 쿠키 삭제
                .permitAll()
                )
                // 비활성 회사 소속 회원 자동 로그아웃 게이트
                .addFilterAfter(new MemberCompanyGuardFilter(companyRepository), AuthorizationFilter.class)
                // 시스템 점검 모드 게이트 — 점검 중 일반 회원 차단 (페이지: /maintenance 로 redirect, API: 503)
                //  ※ MASTER 콘솔(/master/**) 은 @Order(1) 별도 체인이라 이 필터의 영향을 받지 않는다.
                .addFilterAfter(new SystemMaintenanceGuardFilter(systemMaintenanceService), AuthorizationFilter.class);

        return http.build();
    }
}
