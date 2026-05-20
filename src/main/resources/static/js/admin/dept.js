// CSRF 토큰과 헤더 이름을 가져오는 함수
function getCsrfToken() {
    const token = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
    const header = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');
    return { token, header };
}

async function createDept(companyId) {
    const deptNameInput = document.getElementById('deptNameInput');
    const deptName = deptNameInput.value;

    if (!deptName.trim()) {
        alert("부서명을 입력해주세요.");
        return;
    }

    const data = { companyId: companyId, deptName: deptName };

    try {
        const response = await fetch('/api/admin/dept/create', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data)
        });

        if (response.ok) {
            // [핵심] 서버가 보내준 HTML(목록 부분)을 텍스트로 받음
            const htmlChunk = await response.text();

            document.getElementById('deptListContainer').innerHTML = htmlChunk;

            deptNameInput.value = ''; // 입력창만 비우기
            alert("부서가 생성되었습니다.");
        } else {
            alert(await window.getApiErrorMessage(response, "생성 실패"));
        }
    } catch (error) {
        console.error(error);
    }
}

// 1. 수정 모드 <-> 일반 모드 전환 함수
function toggleEditMode(deptId, isEdit) {
    const textSpan = document.getElementById(`dept-name-text-${deptId}`);
    const inputField = document.getElementById(`dept-name-input-${deptId}`);

    const editBtn = document.getElementById(`btn-edit-${deptId}`);
    const saveBtn = document.getElementById(`btn-save-${deptId}`);
    const deleteBtn = document.getElementById(`btn-delete-${deptId}`);
    const cancelBtn = document.getElementById(`btn-cancel-${deptId}`);

    if (isEdit) {
        // 수정 모드로 변경
        textSpan.classList.add('hidden');
        inputField.classList.remove('hidden');
        inputField.focus(); // 바로 입력 가능하게 포커스

        editBtn.classList.add('hidden');
        saveBtn.classList.remove('hidden');
        deleteBtn.classList.add('hidden');
        cancelBtn.classList.remove('hidden');
    } else {
        // 일반 모드로 복귀 (취소 시)
        textSpan.classList.remove('hidden');
        inputField.classList.add('hidden');
        inputField.value = textSpan.innerText; // 입력값 초기화

        editBtn.classList.remove('hidden');
        saveBtn.classList.add('hidden');
        deleteBtn.classList.remove('hidden');
        cancelBtn.classList.add('hidden');
    }
}

// 2. AJAX로 수정한 이름 저장하기
async function saveDept(deptId) {
    const inputField = document.getElementById(`dept-name-input-${deptId}`);
    const newDeptName = inputField.value.trim();

    // 💡 숨겨둔 deptCode 가져오기
    const deptCode = document.getElementById(`dept-code-${deptId}`)?.value;

    if (newDeptName === "") {
        alert("부서명을 입력해주세요.");
        return;
    }

    const { token, header } = getCsrfToken(); // CSRF 토큰 가져오기

    try {
        const response = await fetch(`/api/admin/dept/update/${deptId}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                [header]: token // CSRF 헤더 추가
            },
            body: JSON.stringify({
                deptName: newDeptName,
                deptCode: deptCode
            })
        });

        if (response.ok) {
            // 💡 핵심: 서버가 보내준 HTML 조각을 받습니다.
            const htmlChunk = await response.text();

            // 💡 화면의 컨테이너를 서버가 보내준 따끈따끈한 HTML로 통째로 갈아 끼웁니다.
            const container = document.getElementById('deptListContainer');
            if (container) {
                container.innerHTML = htmlChunk;
                alert("부서명이 수정되었습니다.");
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

async function deleteDept(deptId) {
    if (!confirm("정말로 이 부서를 삭제하시겠습니까?")) return;
    try {
        const response = await fetch(`/api/admin/dept/delete/${deptId}`, {
            method: 'POST'
        });
        if (response.ok) {
            const htmlChunk = await response.text();
            document.getElementById('deptListContainer').innerHTML = htmlChunk;
            alert("부서가 삭제되었습니다.");
        } else {
            alert(await window.getApiErrorMessage(response, "삭제 실패"));
        }
    } catch (error) {
        console.error(error);
    }
}
