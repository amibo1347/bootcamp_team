// CSRF 토큰과 헤더 이름을 가져오는 함수
function getCsrfToken() {
    const token = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
    const header = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');
    return { token, header };
}

async function createPosition(companyId) {
    const positionNameInput = document.getElementById('positionNameInput');
    const positionName = positionNameInput.value;
    const isAdminCheckbox = document.getElementById('isAdminCheck');
    const isAdmin = isAdminCheckbox.checked;

    if (!positionName.trim()) {
        alert("직급명을 입력해주세요.");
        return;
    }

    const data = { companyId: companyId, positionName: positionName, isAdmin: isAdmin };
    const { token, header } = getCsrfToken();

    try {
        const response = await fetch('/api/admin/position/create', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                [header]: token
            },
            body: JSON.stringify(data)
        });

        if (response.ok) {
            // [핵심] 서버가 보내준 HTML(목록 부분)을 텍스트로 받음
            const htmlChunk = await response.text();

            document.getElementById('positionListContainer').innerHTML = htmlChunk;

            positionNameInput.value = ''; // 입력창만 비우기
            alert("직급이 생성되었습니다.");
        } else {
            alert("생성 실패");
        }
    } catch (error) {
        console.error(error);
    }
}

// 1. 수정 모드 <-> 일반 모드 전환 함수
function toggleEditMode(positionId, isEdit) {
    const textSpan = document.getElementById(`pos-name-text-${positionId}`);
    const inputField = document.getElementById(`pos-name-input-${positionId}`);
    const adminCheckbox = document.getElementById(`pos-isAdmin-${positionId}`);

    const editBtn = document.getElementById(`btn-edit-${positionId}`);
    const saveBtn = document.getElementById(`btn-save-${positionId}`);
    const deleteBtn = document.getElementById(`btn-delete-${positionId}`);
    const cancelBtn = document.getElementById(`btn-cancel-${positionId}`);

    if (isEdit) {
        // 수정 모드로 변경
        textSpan.classList.add('hidden');
        inputField.classList.remove('hidden');
        inputField.focus(); // 바로 입력 가능하게 포커스

        editBtn.classList.add('hidden');
        saveBtn.classList.remove('hidden');
        deleteBtn.classList.add('hidden');
        cancelBtn.classList.remove('hidden');

        if(adminCheckbox && textSpan) {
            adminCheckbox.removeAttribute('disabled'); // 체크박스 활성화
            adminCheckbox.checked = textSpan.dataset.isAdmin === 'true'; // 체크박스 상태 반영
        }

    } else {
        // 일반 모드로 복귀 (취소 시)
        textSpan.classList.remove('hidden');
        inputField.classList.add('hidden');
        inputField.value = textSpan.innerText; // 입력값 초기화

        if (adminCheckbox && textSpan) {
            adminCheckbox.setAttribute('disabled', 'disabled'); // 체크박스 비활성화
            adminCheckbox.checked = textSpan.dataset.isAdmin === 'true';
        }

        editBtn.classList.remove('hidden');
        saveBtn.classList.add('hidden');
        deleteBtn.classList.remove('hidden');
        cancelBtn.classList.add('hidden');
    }
}

// 2. AJAX로 수정한 이름 저장하기
async function savePosition(positionId) {
    const inputField = document.getElementById(`pos-name-input-${positionId}`);
    const newPositionName = inputField.value.trim();
    const adminCheckbox = document.getElementById(`pos-isAdmin-${positionId}`);
    const isAdmin = adminCheckbox ? adminCheckbox.checked : false;

    if (newPositionName === "") {
        alert("직급명을 입력해주세요.");
        return;
    }

    const { token, header } = getCsrfToken(); // CSRF 토큰 가져오기

    try {
        const response = await fetch(`/api/admin/position/update/${positionId}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                [header]: token // CSRF 헤더 추가
            },
            body: JSON.stringify({
                positionName: newPositionName,
                isAdmin: isAdmin
            })
        });

        if (response.ok) {
            // 💡 핵심: 서버가 보내준 HTML 조각을 받습니다.
            const htmlChunk = await response.text();

            // 💡 화면의 컨테이너를 서버가 보내준 따끈따끈한 HTML로 통째로 갈아 끼웁니다.
            const container = document.getElementById('positionListContainer');
            if (container) {
                container.innerHTML = htmlChunk;
                alert("직급명이 수정되었습니다.");
            }
        } else {
            // 500 에러 등이 나면 여기서 잡힙니다.
            const errorMsg = await response.text();
            console.error("서버 에러:", errorMsg);
            alert("수정에 실패했습니다. 다시 시도해주세요.");
        }
    } catch (error) {
        console.error("네트워크 오류:", error);
    }
}

async function deletePosition(positionId) {
    if (!confirm("정말로 이 직급을 삭제하시겠습니까?")) return;
    try {
        const response = await fetch(`/api/admin/position/delete/${positionId}`, {
            method: 'POST',
            headers: {
                [getCsrfToken().header]: getCsrfToken().token
            }
        });
        if (response.ok) {
            const htmlChunk = await response.text();
            document.getElementById('positionListContainer').innerHTML = htmlChunk;
            alert("직급이 삭제되었습니다.");
        } else {
            alert("삭제 실패");
        }
    } catch (error) {
        console.error(error);
    }
}
