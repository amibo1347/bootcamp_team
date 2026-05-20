package com.team.intranet.service;

import static dev.samstevens.totp.util.Utils.getDataUriForImage;

import org.springframework.stereotype.Service;

import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;

/**
 * MASTER 계정 TOTP(인증기 앱) 2차 인증 처리.
 *  - dev.samstevens.totp 래핑: 시크릿 생성 / QR 이미지 생성 / 코드 검증.
 *  - 표준 TOTP: SHA1, 6자리, 30초 주기 (Google Authenticator 등과 호환).
 */
@Service
public class MasterTotpService {

    /** 인증기 앱에 표시될 발급자 이름. */
    private static final String ISSUER = "Intranet MASTER";

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final QrGenerator qrGenerator = new ZxingPngQrGenerator();
    private final CodeVerifier codeVerifier =
            new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());

    /** 신규 TOTP 시크릿 생성. */
    public String newSecret() {
        return secretGenerator.generate();
    }

    /**
     * 인증기 앱으로 스캔할 QR 코드를 data URI(PNG base64)로 생성한다.
     * @param loginId 인증기 앱에 표시될 계정 라벨
     */
    public String qrImageDataUri(String secret, String loginId) {
        QrData data = new QrData.Builder()
                .label(loginId)
                .secret(secret)
                .issuer(ISSUER)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();
        try {
            byte[] image = qrGenerator.generate(data);
            return getDataUriForImage(image, qrGenerator.getImageMimeType());
        } catch (QrGenerationException e) {
            throw new IllegalStateException("TOTP QR 코드 생성에 실패했습니다.", e);
        }
    }

    /** 입력 코드가 시크릿 기준으로 유효한지 검증. */
    public boolean verify(String secret, String code) {
        return secret != null && code != null && codeVerifier.isValidCode(secret, code.trim());
    }
}
