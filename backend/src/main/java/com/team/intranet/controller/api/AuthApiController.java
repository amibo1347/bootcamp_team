package com.team.intranet.controller.api;

import com.team.intranet.config.jwt.JwtTokenProvider;
import com.team.intranet.dto.auth.CompanyVerifyResponse;
import com.team.intranet.dto.auth.LoginRequest;
import com.team.intranet.dto.auth.LoginResponse;
import com.team.intranet.dto.auth.SignupRequest;
import com.team.intranet.entity.Member;
import com.team.intranet.enums.member.MemberType;
import com.team.intranet.enums.member.Status;
import com.team.intranet.repository.MemberRepository;
import com.team.intranet.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthApiController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final MemberRepository memberRepository;
    private final MemberService memberService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.loginId(), request.password())
            );

            Member member = memberRepository.findByLoginId(authentication.getName())
                    .orElseThrow(() -> new IllegalStateException("Authenticated member not found: " + authentication.getName()));

            if (member.getStatus() == Status.LEAVE || member.getStatus() == Status.REJECT) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "비활성화된 계정입니다."));
            }

            String token = tokenProvider.createToken(member);
            return ResponseEntity.ok(new LoginResponse(
                    token,
                    tokenProvider.getExpirationMs(),
                    LoginResponse.UserInfo.of(member)
            ));

        } catch (DisabledException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "관리자 승인이 필요한 계정입니다."));
        } catch (BadCredentialsException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "아이디 또는 비밀번호가 일치하지 않습니다."));
        } catch (AuthenticationException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "로그인에 실패했습니다."));
        }
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody SignupRequest request) {
        MemberType result = memberService.join(request.toMemberDto());

        return switch (result) {
            case JOIN_SUCCESS -> ResponseEntity.status(HttpStatus.CREATED).body(
                    Map.of("success", true, "message", "가입 신청이 접수되었습니다. 관리자 승인 후 로그인 가능합니다.")
            );
            case ALREADY_MEMBER -> ResponseEntity.status(HttpStatus.CONFLICT).body(
                    Map.of("success", false, "error", "이미 가입된 아이디입니다.", "code", result.name())
            );
            case NOT_MATCH_PASSWORD -> ResponseEntity.badRequest().body(
                    Map.of("success", false, "error", "비밀번호 확인이 일치하지 않습니다.", "code", result.name())
            );
            case NOT_COMPANY -> ResponseEntity.badRequest().body(
                    Map.of("success", false, "error", "유효하지 않은 기업 코드입니다.", "code", result.name())
            );
        };
    }

    @GetMapping("/company/verify")
    public ResponseEntity<CompanyVerifyResponse> verifyCompany(@RequestParam String companyCode) {
        Long companyId = memberService.getVerifyCompanyId(companyCode);
        if (companyId == null) {
            return ResponseEntity.ok(CompanyVerifyResponse.notFound());
        }
        String logoPath = memberService.getLogoPath(companyId);
        return ResponseEntity.ok(CompanyVerifyResponse.found(companyId, logoPath));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return memberRepository.findByLoginId(authentication.getName())
                .<ResponseEntity<?>>map(m -> ResponseEntity.ok(LoginResponse.UserInfo.of(m)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }
}
