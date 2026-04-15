(() => {
    const getCsrfToken = () => {
        const token = document.querySelector('meta[name="_csrf"]')?.content;
        const header = document.querySelector('meta[name="_csrf_header"]')?.content;
        return { token, header };
    };

    /**
     * 회원 가입 승인 처리
     */
    const approveMember = async (memberId) => {
        // 머스테치에 맞춰 positionId만 가져옴 (이미지 디자인상 부서 선택이 없다면)
        const positionElement = document.getElementById(`position-${memberId}`);
        const positionId = positionElement ? positionElement.value : null;

        if (!positionId) {
            alert("직급을 선택해주세요.");
            return;
        }

        if (!confirm("해당 회원의 가입을 승인하시겠습니까?")) return;

        const { token, header } = getCsrfToken();
        const params = new URLSearchParams();
        // 컨트롤러 요구사항에 따라 필요시 추가 (현재 디자인상으론 position만 있음)
        params.append('positionId', positionId);

        try {
            const response = await fetch(`/admin/accept/${memberId}`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    [header]: token
                },
                body: params
            });

            if (response.redirected) {
                window.location.href = response.url;
                return;
            }

            if (response.ok) {
                alert("성공적으로 승인되었습니다.");
                location.reload();
            } else {
                alert("승인 처리 중 오류가 발생했습니다.");
            }
        } catch (error) {
            console.error('Error:', error);
            alert("서버 통신 중 오류가 발생했습니다.");
        }
    };

    /**
     * 회원 가입 반려(거절) 처리
     */
    const rejectMember = async (memberId) => {
        if (!confirm("가입 신청을 반려하시겠습니까?")) return;

        const { token, header } = getCsrfToken();

        try {
            const response = await fetch(`/admin/reject/${memberId}`, {
                method: 'POST',
                headers: {
                    [header]: token
                }
            });

            if (response.redirected) {
                window.location.href = response.url;
                return;
            }

            if (response.ok) {
                alert("반려 처리가 완료되었습니다.");
                location.reload();
            } else {
                alert("반려 처리 중 오류 발생");
            }
        } catch (error) {
            console.error('Error:', error);
            alert("처리 중 오류가 발생했습니다.");
        }
    };

    window.approveMember = approveMember;
    window.rejectMember = rejectMember;
})();