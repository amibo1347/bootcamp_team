// 모달 열기 및 데이터 채우기
wwindow.openEditModal = (id, name, pos, email, phone, birth, hire, img) => {
    // 1. querySelector를 사용해 각 입력창에 특정 사원의 정보를 넣습니다.
    document.querySelector('#editEmpId').value = id;
    document.querySelector('#modalName').innerText = name; // '이민혁' 대신 실제 이름 주입!
    document.querySelector('#editPosition').value = pos;
    document.querySelector('#editEmail').value = email;
    document.querySelector('#editPhone').value = phone;
    document.querySelector('#editBirth').value = birth;
    document.querySelector('#editHire').value = hire;

    // 2. 프로필 이미지 처리
    const modalImg = document.querySelector('#modalProfileImg');
    const defaultSvg = document.querySelector('#modalDefaultSvg');

    if (img && img !== 'null' && img !== '') {
        modalImg.src = img;
        modalImg.classList.remove('hidden');
        defaultSvg.classList.add('hidden');
    } else {
        modalImg.classList.add('hidden');
        defaultSvg.classList.remove('hidden');
    }

    // 3. 모달 표시
    document.querySelector('#editModal').classList.remove('hidden');
};

// 모달 닫기
window.closeEditModal = () => {
    document.querySelector('#editModal').classList.add('hidden');
};

// 직원 정보 수정 요청 (Fetch)
window.updateMember = async () => {
    const id = document.querySelector('#editEmpId').value;

    // 객체로 묶어서 전송 데이터 준비
    const data = {
        position: document.querySelector('#editPosition').value,
        email: document.querySelector('#editEmail').value,
        phone: document.querySelector('#editPhone').value,
        birthDate: document.querySelector('#editBirth').value,
        hireDate: document.querySelector('#editHire').value
    };

    // Meta 태그에서 CSRF 정보 추출
    const token = document.querySelector('meta[name="_csrf"]')?.content;
    const header = document.querySelector('meta[name="_csrf_header"]')?.content;

    try {
        const response = await fetch(`/api/members/${id}`, {
            method: 'PUT',
            headers: {
                [header]: token,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(data)
        });

        if (response.ok) {
            alert("정보가 성공적으로 수정되었습니다.");
            location.reload(); // 변경사항 반영을 위해 페이지 새로고침
        } else {
            const errorText = await response.text();
            alert(`수정 실패: ${errorText || '서버 오류가 발생했습니다.'}`);
        }
    } catch (error) {
        console.error("Update Error:", error);
        alert("통신 중 오류가 발생했습니다.");
    }
};


// 직원 정보 삭제
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