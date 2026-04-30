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
            alert("생성 실패");
        }
    } catch (error) {
        console.error(error);
    }
}

// dept.js 파일 수정
async function editDept(deptId) {
    // 1. 사용자에게 새 부서명을 입력받음
    const newDeptName = prompt("새로운 부서명을 입력하세요.");

    // 2. 취소를 누르거나 빈 값을 입력하면 종료
    if (newDeptName === null || newDeptName.trim() === "") {
        return;
    }

    try {
        const response = await fetch(`/api/admin/dept/update/${deptId}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ deptName: newDeptName })
        });

        if (response.ok) {
            alert("부서명이 수정되었습니다.");
            location.reload(); 
        }
    } catch (error) {
        console.error("수정 실패:", error);
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
            alert("삭제 실패");
        }
    } catch (error) {
        console.error(error);
    }
}
