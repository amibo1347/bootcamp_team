/**
 * [내 프로필]
 *  - 프로필 사진: POST /api/member/me/profileImg (memberList 미리보기 패턴)
 *  - 개인 정보: POST /api/member/me/update (memberList FormData 패턴, 부서·직급 제외)
 */

const profileImgInput = document.querySelector('#profileImgInput');
const profileSummaryImg = document.querySelector('#profileSummaryImg');
const profileSummaryRoot = document.querySelector('#profileSummaryRoot');
const profilePageData = document.querySelector('#profilePageData');
const profileEditModal = document.querySelector('#profileEditModal');

// ---------------------------------------------------------------------------
// 프로필 사진 변경
// ---------------------------------------------------------------------------
if (profileImgInput && profileSummaryImg) {
    /**
     * 파일 선택 시 미리보기 후 서버에 업로드
     * @param {Event} e - change 이벤트
     */
    profileImgInput.addEventListener('change', async function (e) {
        const file = e.target.files[0];
        if (!file) return;

        if (!file.type.startsWith('image/')) {
            alert('이미지 파일만 업로드 가능합니다.');
            e.target.value = '';
            return;
        }

        const reader = new FileReader();
        reader.onload = function (event) {
            profileSummaryImg.src = event.target.result;
        };
        reader.readAsDataURL(file);

        const formData = new FormData();
        formData.append('profileImg', file);

        try {
            const response = await fetch('/api/member/me/profileImg', {
                method: 'POST',
                body: formData,
            });

            if (response.ok) {
                alert('프로필 사진이 변경되었습니다.');
                const baseUrl = profileSummaryRoot?.dataset.profileImgUrl;
                if (baseUrl) {
                    profileSummaryImg.src = `${baseUrl}?t=${Date.now()}`;
                }
                document.querySelectorAll('img[src*="/profileImg"]').forEach((img) => {
                    if (img === profileSummaryImg) return;
                    const src = img.getAttribute('src') || '';
                    if (src.includes('/profileImg')) {
                        img.src = src.split('?')[0] + '?t=' + Date.now();
                    }
                });
            } else {
                alert(await window.getApiErrorMessage(response, '사진 변경에 실패했습니다.'));
            }
        } catch (error) {
            console.error('Profile image upload error:', error);
            alert('통신 중 오류가 발생했습니다.');
        } finally {
            e.target.value = '';
        }
    });
}

// ---------------------------------------------------------------------------
// 개인 정보 수정 모달 (기존 API: POST /api/member/me/update)
// ---------------------------------------------------------------------------

/**
 * CSRF 헤더 객체 반환 (있을 때만)
 * @returns {Record<string, string>}
 */
function getCsrfHeaders() {
    const token = document.querySelector('meta[name="_csrf"]')?.content;
    const header = document.querySelector('meta[name="_csrf_header"]')?.content;
    const headers = {};
    if (token && header) {
        headers[header] = token;
    }
    return headers;
}

/**
 * 수정 모달 열기 — #profilePageData dataset 으로 폼 채움
 */
window.openProfileEditModal = () => {
    if (!profileEditModal) return;

    const seed = profilePageData?.dataset || {};
    document.querySelector('#profileEditName').value = seed.name || '';
    document.querySelector('#profileEditEmail').value = seed.email || '';
    document.querySelector('#profileEditPhone').value = seed.phone || '';
    document.querySelector('#profileEditBirth').value = seed.birth || '';

    profileEditModal.classList.remove('hidden');
};

/**
 * 수정 모달 닫기
 */
window.closeProfileEditModal = () => {
    profileEditModal?.classList.add('hidden');
};

/**
 * 개인 정보 저장 — MemberApiController POST /api/member/me/update
 */
window.updateMyProfile = async () => {
    const formData = new FormData();
    formData.append('name', document.querySelector('#profileEditName').value.trim());
    formData.append('email', document.querySelector('#profileEditEmail').value.trim());
    formData.append('phone', document.querySelector('#profileEditPhone').value.trim());
    const birthRaw = document.querySelector('#profileEditBirth').value.trim();
    if (birthRaw) {
        formData.append('birthDay', birthRaw);
    }

    try {
        const response = await fetch('/api/member/me/update', {
            method: 'POST',
            headers: getCsrfHeaders(),
            body: formData,
        });

        if (response.ok) {
            alert('정보가 성공적으로 수정되었습니다.');
            closeProfileEditModal();
            window.location.reload();
        } else {
            alert(await window.getApiErrorMessage(response, '정보 수정에 실패했습니다.'));
        }
    } catch (error) {
        console.error('Profile update error:', error);
        alert('통신 중 오류가 발생했습니다.');
    }
};

if (profileEditModal) {
    profileEditModal.addEventListener('click', (event) => {
        if (event.target === profileEditModal) {
            closeProfileEditModal();
        }
    });
}
