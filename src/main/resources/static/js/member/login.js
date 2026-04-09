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

        try {
            // 1. 백엔드가 만든 API로 데이터 전송
            const response = await fetch('/api/verify-code', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-CSRF-TOKEN': document.querySelector('input[name="_csrf"]').value // hidden input에서 토큰 가져오기
                },
                body: JSON.stringify({ accessCode: userInput })
            });

            if (!response.ok) throw new Error('네트워크 응답 에러');

            // 2. 백엔드가 보내준 결과 받기 { success: true/false }
            const result = await response.json();

            if (result.success) {
                // 성공: 가입 페이지로 이동
                location.href = '/member/signup';
            } else {
                // 실패: 에러 메시지 처리 (주형님 스타일대로 시각적 피드백)
                alert("인증 코드가 일치하지 않습니다.");
                $accessCodeInput.value = '';
                $accessCodeInput.focus();
                // 여기서 input 테두리를 빨간색으로 바꾸는 함수를 호출하면 더 좋겠죠?
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