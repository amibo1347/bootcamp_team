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

// ---------------------------------------------------------------------------
// 비밀번호 변경 모달 (MASTER /master/account/password 와 동일 입력 패턴)
//  - 엔드포인트: POST /api/member/me/password
//  - 입력값 검증·현재 비번 일치 확인은 서버에서 수행, 클라이언트는 비어있는지만 가볍게 체크
// ---------------------------------------------------------------------------

const passwordChangeModal = document.querySelector('#passwordChangeModal');

/** 입력 필드 3개 초기화 */
function resetPasswordChangeForm() {
    const cur = document.querySelector('#pwdCurrent');
    const nw = document.querySelector('#pwdNew');
    const cf = document.querySelector('#pwdConfirm');
    if (cur) cur.value = '';
    if (nw) nw.value = '';
    if (cf) cf.value = '';
}

/** 비밀번호 변경 모달 열기 */
window.openPasswordChangeModal = () => {
    if (!passwordChangeModal) return;
    resetPasswordChangeForm();
    passwordChangeModal.classList.remove('hidden');
    document.querySelector('#pwdCurrent')?.focus();
};

/** 비밀번호 변경 모달 닫기 */
window.closePasswordChangeModal = () => {
    passwordChangeModal?.classList.add('hidden');
};

/** 비밀번호 변경 제출 — MemberApiController POST /api/member/me/password */
window.submitPasswordChange = async () => {
    const currentPassword = document.querySelector('#pwdCurrent')?.value || '';
    const newPassword = document.querySelector('#pwdNew')?.value || '';
    const confirmPassword = document.querySelector('#pwdConfirm')?.value || '';

    // 클라이언트 가벼운 사전 체크 (서버에서 동일 검증을 다시 한 번 수행함)
    if (!currentPassword || !newPassword || !confirmPassword) {
        alert('모든 항목을 입력하세요.');
        return;
    }
    if (newPassword.length < 8) {
        alert('새 비밀번호는 8자 이상이어야 합니다.');
        return;
    }
    if (newPassword !== confirmPassword) {
        alert('새 비밀번호와 확인이 일치하지 않습니다.');
        return;
    }

    const formData = new FormData();
    formData.append('currentPassword', currentPassword);
    formData.append('newPassword', newPassword);
    formData.append('confirmPassword', confirmPassword);

    try {
        const response = await fetch('/api/member/me/password', {
            method: 'POST',
            headers: getCsrfHeaders(),
            body: formData,
        });

        // 서버는 success/message 를 JSON 으로 내려준다 (200 또는 400)
        let payload = null;
        try { payload = await response.json(); } catch (_) { /* no body */ }

        if (response.ok && payload?.success) {
            alert(payload.message || '비밀번호가 변경되었습니다.');
            closePasswordChangeModal();
            return;
        }

        alert(payload?.message
            || await window.getApiErrorMessage(response, '비밀번호 변경에 실패했습니다.'));
    } catch (error) {
        console.error('Password change error:', error);
        alert('통신 중 오류가 발생했습니다.');
    }
};

if (passwordChangeModal) {
    passwordChangeModal.addEventListener('click', (event) => {
        if (event.target === passwordChangeModal) {
            closePasswordChangeModal();
        }
    });
}
