(() => {
    const $btnShowModal = document.querySelector('#btn-show-modal');
    const $codeModal = document.querySelector('#code-modal');
    const $btnCodeConfirm = document.querySelector('#btn-code-confirm');
    const $btnCodeCancel = document.querySelector('#btn-code-cancel');
    const $accessCodeInput = document.querySelector('#access-code');

    if (!$btnShowModal || !$codeModal) return;

    // 모달 열기/닫기
    const toggleModal = (isOpen) => {
        $codeModal.style.display = isOpen ? 'flex' : 'none';
        if (isOpen) {
            $accessCodeInput.value = '';
            $accessCodeInput.focus();
        }
    };

    /**
     * [프런트 핵심 로직] 서버에 코드 검증 요청
     */
    const handleVerifyCode = async () => {
        const userInput = $accessCodeInput.value.trim();

        if (!userInput) {
            alert("기업 코드를 입력해주세요.");
            return;
        }

        // 현재 회사 로그인 페이지(/{도메인}/login)의 회사 id — 이 회사 코드만 인증 허용.
        const pageCompanyId = document.querySelector('input[name="companyId"]')?.value || '';

        try {
            const response = await fetch(
                `/api/member/company/verify?companyCode=${encodeURIComponent(userInput)}`
                + `&expectedCompanyId=${encodeURIComponent(pageCompanyId)}`, {
                method: 'GET'
            });

            if (!response.ok) {
                alert(await window.getApiErrorMessage(response, '서버 통신 중 오류가 발생했습니다.'));
                return;
            }

            // 1. 서버가 { "isVerify": true, "companyId": ... } 객체를 보냅니다.
            const result = await response.json();
            console.log("서버 응답 데이터:", result);

            // 2. result 자체가 아니라 result 안의 'isVerify' 속성을 확인해야 합니다.
            if (result.isVerify) {
                // 성공: 현재 회사의 회원가입 페이지(/{도메인}/signup)로 이동.
                alert("인증에 성공했습니다");
                const companyDomain = document.querySelector('input[name="companyDomain"]')?.value || '';
                location.href = `/${encodeURIComponent(companyDomain)}/signup`;
            } else {
                alert(result.message || "인증 코드가 일치하지 않습니다.");
                $accessCodeInput.value = '';
                $accessCodeInput.focus();
            }

        } catch (error) {
            console.error('인증 에러:', error);
            alert("서버 통신 중 오류가 발생했습니다.");
        }
    };

    // 이벤트 바인딩
    $btnShowModal.addEventListener('click', () => toggleModal(true));
    $btnCodeCancel?.addEventListener('click', () => toggleModal(false));
    $btnCodeConfirm?.addEventListener('click', handleVerifyCode);

    // 엔터키 지원
    $accessCodeInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') handleVerifyCode();
    });

})();