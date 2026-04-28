function fetchMembersByDept() {
    const deptId = document.querySelector('#deptSelect').value;
    const container = document.querySelector('#memberListContainer');

    fetch(`/admin/memberList/filter?deptId=${deptId}`)
        .then(response => {
            if (!response.ok) throw new Error('Network response was not ok');
            return response.text();
        })
        .then(html => {
            // 받아온 HTML 조각으로 컨테이너 내용 교체
            container.innerHTML = html;
        })
        .catch(error => {
            console.error('필터링 중 오류 발생:', error);
        });
}

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

// 파일 입력창의 값이 변했을 때(파일이 선택되었을 때) 실행
document.querySelector('#profileImgInput').addEventListener('change', function (e) {
    const file = e.target.files[0]; // 사용자가 선택한 첫 번째 파일

    if (file) {
        // 1. 이미지 파일인지 확인 (보안 및 오류 방지)
        if (!file.type.startsWith('image/')) {
            alert('이미지 파일만 업로드 가능합니다.');
            return;
        }

        // 2. 파일을 읽어서 미리보기 생성
        const reader = new FileReader();
        reader.onload = function (event) {
            const modalImg = document.querySelector('#modalProfileImg');
            const defaultSvg = document.querySelector('#modalDefaultSvg');

            // 이미지 소스를 읽어온 데이터로 교체
            modalImg.src = event.target.result;
            modalImg.classList.remove('hidden');
            defaultSvg.classList.add('hidden');
        };

        reader.readAsDataURL(file); // 파일을 URL 형태로 읽어옵니다.
    }
});

// 직원 정보 수정 요청 (Fetch)
window.updateMember = async () => {
    const id = document.querySelector('#editEmpId').value;
    const fileInput = document.querySelector('#profileImgInput');

    // 데이터를 담을 바구니(FormData) 생성
    const formData = new FormData();
    formData.append('position', document.querySelector('#editPosition').value);
    formData.append('email', document.querySelector('#editEmail').value);
    formData.append('phone', document.querySelector('#editPhone').value);
    formData.append('birthDate', document.querySelector('#editBirth').value);
    formData.append('hireDate', document.querySelector('#editHire').value);

    // 만약 파일이 선택되었다면 파일도 추가
    if (fileInput.files[0]) {
        formData.append('profileImg', fileInput.files[0]);
    }

    // Meta 태그에서 CSRF 정보 추출
    const token = document.querySelector('meta[name="_csrf"]')?.content;
    const header = document.querySelector('meta[name="_csrf_header"]')?.content;

    try {
        const response = await fetch(`/api/members/${id}`, {
            method: 'PUT',
            headers: {
                [header]: token
                // 주의: FormData를 보낼 때는 'Content-Type' 헤더를 명시하지 않아야 브라우저가 자동으로 설정합니다.
            },
            body: formData
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