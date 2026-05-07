async function apiCall(url, options = {}) {
    const response = await fetch(url, options);
    
    if (response.ok) {
        // 응답이 비어있을 수도 있으니까 안전하게 처리
        const text = await response.text();
        return text ? JSON.parse(text) : null;
    }
    
    // 에러 응답
    let errorMessage = "요청 처리 중 오류가 발생했습니다";
    try {
        const error = await response.json();
        errorMessage = error.message || errorMessage;
    } catch (e) {
        // JSON이 아닌 응답일 경우 기본 메시지 사용
    }
    
    throw new Error(errorMessage);
}