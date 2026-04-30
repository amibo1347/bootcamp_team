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