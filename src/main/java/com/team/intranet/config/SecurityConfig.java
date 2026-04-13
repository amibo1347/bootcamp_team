package com.team.intranet.config;

import com.team.intranet.config.LoginFailureHandler;
import com.team.intranet.config.LoginSuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@RequiredArgsConstructor // 핸들러 주입을 위해 추가
public class SecurityConfig {

    private final LoginSuccessHandler loginSuccessHandler;
    private final LoginFailureHandler loginFailureHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/**", "/company/**")
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/css/**", "/js/**", "/images/**", "/favicon.ico",
                    "/error", "/index", "/calendar", "/member/**", "/api/**"
                ).permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN") // 💡 관리자 권한 체크 추가
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/member/login")
                .loginProcessingUrl("/member/login") // 💡 HTML <form action="/member/login">과 일치
                .usernameParameter("loginId")        // 💡 HTML <input name="loginId">와 일치
                .passwordParameter("password")       // 💡 HTML <input name="password">와 일치
                .successHandler(loginSuccessHandler) // 💡 로그인 성공 시 세션 처리기
                .failureHandler(loginFailureHandler) // 💡 로그인 실패 시 에러 처리기
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/member/logout")
                .logoutSuccessUrl("/index")
                .invalidateHttpSession(true) // 💡 로그아웃 시 세션 무효화
                .permitAll()
            );

        return http.build();
    }
}