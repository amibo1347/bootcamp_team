// CSRF 토큰과 헤더 이름을 가져오는 함수
function getCsrfToken() {
    const token = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
    const header = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');
    return { token, header };
}

async function createPosition(companyId) {
    const positionNameInput = document.getElementById('positionNameInput');
    const positionName = positionNameInput.value;
    const positionLevelInput = document.getElementById('positionLevelInput');
    const positionLevel = parseInt(positionLevelInput.value, 10) || 0;

    if (!positionName.trim()) {
        alert("직급명을 입력해주세요.");
        return;
    }

    // isAdmin 은 항상 false: 권한 부여는 [권한 관리] 페이지에서 한다.
    const data = { companyId: companyId, positionName: positionName, isAdmin: false, positionLevel: positionLevel };
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
            positionLevelInput.value = '';
            alert("직급이 생성되었습니다.");
        } else {
            alert("생성 실패");
        }
    } catch (error) {
        console.error(error);
    }
}

// 1. 수정 모드 <-> 일반 모드 전환 함수
//    ※ 관리자 체크박스는 제거되었음. 이름/레벨만 토글한다.
function toggleEditMode(positionId, isEdit) {
    const textSpan = document.getElementById(`pos-name-text-${positionId}`);
    const inputField = document.getElementById(`pos-name-input-${positionId}`);
    const levelTextSpan = document.getElementById(`pos-level-text-${positionId}`);
    const levelInputField = document.getElementById(`pos-level-input-${positionId}`);

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

        if (levelTextSpan && levelInputField) {
            levelTextSpan.classList.add('hidden');
            levelInputField.classList.remove('hidden');
        }

    } else {
        // 일반 모드로 복귀 (취소 시)
        textSpan.classList.remove('hidden');
        inputField.classList.add('hidden');
        inputField.value = textSpan.innerText; // 입력값 초기화

        if (levelTextSpan && levelInputField) {
            levelTextSpan.classList.remove('hidden');
            levelInputField.classList.add('hidden');
            levelInputField.value = levelTextSpan.innerText; // 입력값 원복
        }

        editBtn.classList.remove('hidden');
        saveBtn.classList.add('hidden');
        deleteBtn.classList.remove('hidden');
        cancelBtn.classList.add('hidden');
    }
}

// 2. AJAX로 수정한 이름 저장하기
//    ※ 관리자 여부(isAdmin)는 사용자가 직접 토글하지 않는다.
//    ※ 서버는 isAdmin 파라미터를 받으면 기존 값 유지를 위해 현재 직급의 role 을 사용한다.
async function savePosition(positionId) {
    const inputField = document.getElementById(`pos-name-input-${positionId}`);
    const newPositionName = inputField.value.trim();
    const levelInputField = document.getElementById(`pos-level-input-${positionId}`);
    const positionLevel = levelInputField ? (parseInt(levelInputField.value, 10) || 0) : 0;
    // 현재 행에 표시된 뱃지로 관리자 여부를 다시 보낸다 (이름/레벨만 수정 의도이므로 권한 상태는 유지).
    const textSpan = document.getElementById(`pos-name-text-${positionId}`);
    const isAdmin = textSpan && textSpan.dataset.isAdmin === 'true';

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
                isAdmin: isAdmin,
                positionLevel: positionLevel
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
