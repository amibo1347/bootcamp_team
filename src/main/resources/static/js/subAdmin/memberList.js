document.addEventListener('DOMContentLoaded', () => {
    const deptSelect = document.querySelector('#deptSelect');

    document.addEventListener('click', (event) => {
        const editButton = event.target.closest('.js-edit-member-btn');
        if (editButton) {
            openEditModal(editButton);
        }
    });
});

// 모달 열기 및 데이터 채우기
window.openEditModal = (button) => {
    document.querySelector('#editEmpId').value = button.dataset.memberId || '';
    document.querySelector('#editName').value = button.dataset.memberName || '';
    document.querySelector('#editDept').value = button.dataset.deptId || '';
    document.querySelector('#editPosition').value = button.dataset.positionId || '';
    document.querySelector('#editEmail').value = button.dataset.email || '';
    document.querySelector('#editPhone').value = button.dataset.phone || '';
    document.querySelector('#editBirth').value = button.dataset.birth || '';

    // 2. 프로필 이미지 처리
    const modalImg = document.querySelector('#modalProfileImg');
    const defaultSvg = document.querySelector('#modalDefaultSvg');
    const img = button.dataset.profileImg || '';

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
    formData.append('name', document.querySelector('#editName').value);
    formData.append('deptId', document.querySelector('#editDept').value);
    formData.append('positionId', document.querySelector('#editPosition').value);
    formData.append('email', document.querySelector('#editEmail').value);
    formData.append('phone', document.querySelector('#editPhone').value);
    formData.append('birthDay', document.querySelector('#editBirth').value);

    // 만약 파일이 선택되었다면 파일도 추가
    if (fileInput.files[0]) {
        formData.append('profileImg', fileInput.files[0]);
    }

    // Meta 태그에서 CSRF 정보 추출
    const token = document.querySelector('meta[name="_csrf"]')?.content;
    const header = document.querySelector('meta[name="_csrf_header"]')?.content;

    try {
        const response = await fetch(`/api/subAdmin/update/${id}`, {
            method: 'POST',
            headers: {
                [header]: token
                // 주의: FormData를 보낼 때는 'Content-Type' 헤더를 명시하지 않아야 브라우저가 자동으로 설정합니다.
            },
            body: formData
        });

        if (response.ok) {
            alert("정보가 성공적으로 수정되었습니다.");
            closeEditModal();
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
window.deleteMember = async (memberId) => {
    if (!confirm("정말로 이 직원을 퇴사 처리하시겠습니까?")) return;

        // 2단계: CSRF 토큰 및 헤더 정보 추출
        const token = document.querySelector('meta[name="_csrf"]')?.content;
        const header = document.querySelector('meta[name="_csrf_header"]')?.content;

    try {
        const response = await fetch(`/api/subAdmin/fire/${memberId}`, {
            method: 'POST',
            headers: {
                [header]: token
            }
        });

        if (response.ok) {
            alert("퇴사 처리되었습니다.");
            location.reload();
        } else {
            const errorText = await response.text();
            alert(`처리 실패: ${errorText || '알 수 없는 오류가 발생했습니다.'}`);
        }
    } catch (error) {
        console.error("Error during deletion:", error);
        alert("서버와 통신 중 오류가 발생했습니다.");
    }
};

/**
 * 직원 목록을 AJAX로 로드하는 공통 함수
 */
function loadMemberList() {
    const form = document.getElementById('filterForm');
    if (!form) return;

    // 폼 안의 모든 데이터(deptId, name, sort 등)를 가져옴
    const formData = new FormData(form);
    const params = new URLSearchParams(formData);
    
    // AJAX 요청 URL 생성
    const url = `${form.action}?${params.toString()}`;

    fetch(url, {
        method: 'GET',
        headers: {
            'X-Requested-With': 'XMLHttpRequest'
        }
    })
    .then(response => {
        if (!response.ok) throw new Error('네트워크 응답에 문제가 있습니다.');
        return response.text();
    })
    .then(html => {
        const container = document.getElementById('memberListContainer');
        if (container) {
            container.innerHTML = html;
            // 💡 중요: 목록이 새로 바뀌면 내부의 버튼(수정/삭제 등) 이벤트가 
            // 끊길 수 있으므로, 여기서 필요한 초기화 함수를 다시 호출해줘야 함
            // 예: initEditButtons();
        }
    })
    .catch(error => {
        console.error('AJAX 로드 실패:', error);
        alert('목록을 불러오는 중 오류가 발생했습니다.');
    });
}

/**
 * 정렬 버튼 클릭 시 호출
 */
function submitWithSort(sortValue) {
    const sortInput = document.getElementById('sortInput');
    if (sortInput) {
        sortInput.value = sortValue;
        loadMemberList(); // 정렬 값 세팅 후 AJAX 호출
    }
}