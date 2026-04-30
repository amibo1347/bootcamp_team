async function createDept(companyId) {
    const deptNameInput = document.getElementById('deptNameInput');
    const deptName = deptNameInput.value;

    if (!deptName.trim()) {
        alert("부서명을 입력해주세요.");
        return;
    }
    
    // 서버로 보낼 데이터 객체
    const data = {
        companyId: companyId,
        deptName: deptName,
    };

    try {
        const response = await fetch('/api/admin/dept/create', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(data)
        });

        if (response.ok) {
            alert("부서가 성공적으로 생성되었습니다.");
            deptNameInput.value = ''; // 입력창 초기화
            location.reload(); // 간단하게 목록 갱신을 위해 새로고침
        } else {
            const errorText = await response.text();
            alert("에러 발생: " + errorText);
        }
    } catch (error) {
        console.error("Network Error:", error);
        alert("서버와 통신 중 오류가 발생했습니다.");
    }
}