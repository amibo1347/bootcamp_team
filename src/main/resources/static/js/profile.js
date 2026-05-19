/**
 * [내 프로필] 프로필 사진 변경
 *  - subAdmin/memberList.js 의 #profileImgInput change 핸들러·미리보기 패턴과 동일.
 *  - 저장은 직원 모달과 달리 파일 선택 직후 POST /api/member/me/profileImg 로 즉시 반영.
 */

const profileImgInput = document.querySelector('#profileImgInput');
const profileSummaryImg = document.querySelector('#profileSummaryImg');
const profileSummaryRoot = document.querySelector('#profileSummaryRoot');

if (profileImgInput && profileSummaryImg) {
    /**
     * 파일 선택 시 미리보기 후 서버에 업로드
     * @param {Event} e - change 이벤트
     */
    profileImgInput.addEventListener('change', async function (e) {
        const file = e.target.files[0];

        if (!file) {
            return;
        }

        if (!file.type.startsWith('image/')) {
            alert('이미지 파일만 업로드 가능합니다.');
            e.target.value = '';
            return;
        }

        // 1) 미리보기 (memberList 와 동일: FileReader → img.src)
        const reader = new FileReader();
        reader.onload = function (event) {
            profileSummaryImg.src = event.target.result;
        };
        reader.readAsDataURL(file);

        // 2) 서버 저장
        const formData = new FormData();
        formData.append('profileImg', file);

        const token = document.querySelector('meta[name="_csrf"]')?.content;
        const header = document.querySelector('meta[name="_csrf_header"]')?.content;
        const headers = {};
        if (token && header) {
            headers[header] = token;
        }

        try {
            const response = await fetch('/api/member/me/profileImg', {
                method: 'POST',
                headers,
                body: formData,
            });

            if (response.ok) {
                alert('프로필 사진이 변경되었습니다.');
                const baseUrl = profileSummaryRoot?.dataset.profileImgUrl;
                if (baseUrl) {
                    profileSummaryImg.src = `${baseUrl}?t=${Date.now()}`;
                }
                // 헤더 등 다른 영역 프로필 이미지도 갱신
                document.querySelectorAll('img[src*="/profileImg"]').forEach((img) => {
                    if (img === profileSummaryImg) return;
                    const src = img.getAttribute('src') || '';
                    if (src.includes('/profileImg')) {
                        img.src = src.split('?')[0] + '?t=' + Date.now();
                    }
                });
            } else {
                const errorText = await response.text();
                alert(`사진 변경 실패: ${errorText || '서버 오류가 발생했습니다.'}`);
            }
        } catch (error) {
            console.error('Profile image upload error:', error);
            alert('통신 중 오류가 발생했습니다.');
        } finally {
            e.target.value = '';
        }
    });
}
