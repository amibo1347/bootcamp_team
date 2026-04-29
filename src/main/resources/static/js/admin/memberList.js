((memberId) => {
    // 1단계: 전역 범위에서 호출할 수 있도록 함수 정의
    window.deleteMember = async (memberId) => {
        if (!confirm("정말로 이 직원을 삭제하시겠습니까?")) return;

        // 2단계: CSRF 토큰 및 헤더 정보 추출
        const token = document.querySelector('meta[name="_csrf"]')?.content;
        const header = document.querySelector('meta[name="_csrf_header"]')?.content;

        try {
            // 3단계: Fetch API 호출 (await 사용)
            const response = await fetch(`/api/members/${memberId}`, {
                method: 'DELETE',
                headers: {
                    [header]: token,
                    'Content-Type': 'application/json'
                }
            });

            // 4단계: 결과 처리
            if (response.ok) {
                alert("삭제되었습니다.");
                location.reload();
            } else {
                const errorData = await response.json().catch(() => ({}));
                alert(`삭제 실패: ${errorData.message || '알 수 없는 오류가 발생했습니다.'}`);
            }
        } catch (error) {
            // 네트워크 오류 등 예외 처리
            console.error("Error during deletion:", error);
            alert("서버와 통신 중 오류가 발생했습니다.");
        }
    };
})();