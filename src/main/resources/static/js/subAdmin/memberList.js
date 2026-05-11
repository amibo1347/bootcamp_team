const customSelectMap = new Map();

function closeAllCustomSelects() {
    customSelectMap.forEach(({ menu, trigger, container }) => {
        menu.classList.add('hidden');
        trigger.setAttribute('aria-expanded', 'false');
        if (container) {
            container.style.zIndex = '';
        }
    });
}

function syncCustomSelect(selectId) {
    const ui = customSelectMap.get(selectId);
    if (!ui) return;

    const { select, triggerText, menu } = ui;
    const selectedOption = select.options[select.selectedIndex];
    triggerText.textContent = selectedOption ? selectedOption.textContent : '선택하세요';

    menu.querySelectorAll('button[data-value]').forEach((button) => {
        const isSelected = button.dataset.value === select.value;
        button.classList.toggle('bg-indigo-50', isSelected);
        button.classList.toggle('text-indigo-700', isSelected);
        button.classList.toggle('font-semibold', isSelected);
    });
}

function createCustomSelect(select) {
    if (!select || select.dataset.customized === 'true') return;

    const container = select.parentElement;
    const wrapper = document.createElement('div');
    wrapper.className = 'relative';

    const trigger = document.createElement('button');
    trigger.type = 'button';
    trigger.className =
        'w-full rounded border border-stroke bg-white py-2.5 px-4 text-left text-black focus:outline-none focus:ring-2 focus:ring-indigo-300 dark:border-form-strokedark dark:bg-meta-4 dark:text-white';
    trigger.setAttribute('aria-haspopup', 'listbox');
    trigger.setAttribute('aria-expanded', 'false');

    const triggerInner = document.createElement('div');
    triggerInner.className = 'flex items-center justify-between';
    const triggerText = document.createElement('span');
    triggerText.className = 'truncate';
    const arrow = document.createElement('span');
    arrow.className = 'ml-2 text-gray-500';
    arrow.textContent = '▾';
    triggerInner.appendChild(triggerText);
    triggerInner.appendChild(arrow);
    trigger.appendChild(triggerInner);

    const menu = document.createElement('div');
    menu.className = 'absolute left-0 top-full z-[10001] mt-1 hidden max-h-64 w-full overflow-auto rounded-lg border border-gray-200 bg-white shadow-lg';
    menu.setAttribute('role', 'listbox');

    Array.from(select.options).forEach((option) => {
        const item = document.createElement('button');
        item.type = 'button';
        item.dataset.value = option.value;
        item.disabled = option.disabled;
        item.className = 'block w-full px-4 py-2 text-left text-sm text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:text-gray-300';
        item.textContent = option.textContent;
        item.addEventListener('click', () => {
            select.value = option.value;
            select.dispatchEvent(new Event('change', { bubbles: true }));
            closeAllCustomSelects();
        });
        menu.appendChild(item);
    });

    trigger.addEventListener('click', (e) => {
        e.stopPropagation();
        const isOpen = !menu.classList.contains('hidden');
        closeAllCustomSelects();
        if (!isOpen) {
            if (container) container.style.zIndex = '9999';
            menu.classList.remove('hidden');
            trigger.setAttribute('aria-expanded', 'true');
        }
    });

    select.classList.add('hidden');
    select.insertAdjacentElement('afterend', wrapper);
    wrapper.appendChild(trigger);
    wrapper.appendChild(menu);
    select.dataset.customized = 'true';

    customSelectMap.set(select.id, { select, trigger, triggerText, menu, container });
    select.addEventListener('change', () => syncCustomSelect(select.id));
    syncCustomSelect(select.id);
}

function initCustomSelects() {
    document.querySelectorAll('.js-custom-select').forEach((select) => createCustomSelect(select));
}

document.addEventListener('DOMContentLoaded', () => {
    const editModal = document.querySelector('#editModal');
    if (editModal && editModal.parentElement !== document.body) {
        document.body.appendChild(editModal);
    }

    // 퇴사 사유 선택 모달도 body 최상단으로 끌어올려서 z-index/overflow 이슈를 피한다.
    const resignModal = document.querySelector('#resignTypeModal');
    if (resignModal && resignModal.parentElement !== document.body) {
        document.body.appendChild(resignModal);
    }

    initCustomSelects();
    document.addEventListener('click', () => closeAllCustomSelects());

    // 행별 버튼은 fragment 가 AJAX 로 갈아끼워지므로 document 위임 방식으로 일괄 처리한다.
    document.addEventListener('click', (event) => {
        const editButton = event.target.closest('.js-edit-member-btn');
        if (editButton) {
            openEditModal(editButton);
            return;
        }

        const resignButton = event.target.closest('.js-resign-btn');
        if (resignButton) {
            openResignModal(resignButton);
        }
    });
});


// 모달 열기 및 데이터 채우기
window.openEditModal = (button) => {
    document.querySelector('#editEmpId').value = button.dataset.memberId || '';
    document.querySelector('#editName').value = button.dataset.memberName || '';
    document.querySelector('#editDept').value = button.dataset.deptId || '';
    document.querySelector('#editPosition').value = button.dataset.positionId || '';
    document.querySelector('#editDept').dispatchEvent(new Event('change', { bubbles: true }));
    document.querySelector('#editPosition').dispatchEvent(new Event('change', { bubbles: true }));
    document.querySelector('#editEmail').value = button.dataset.email || '';
    document.querySelector('#editPhone').value = button.dataset.phone || '';
    document.querySelector('#editBirth').value = button.dataset.birth || '';

    // 2. 프로필 이미지 처리
    const modalImg = document.querySelector('#modalProfileImg');
    const defaultSvg = document.querySelector('#modalDefaultSvg');
    const img = button.dataset.profileImg || '';

    if (img && img !== 'null' && img !== '') {
        modalImg.src = img;
        modalImg.classList.remove('hidden');
        defaultSvg.classList.add('hidden');
    } else {
        modalImg.classList.add('hidden');
        defaultSvg.classList.remove('hidden');
    }

    // 3. 모달 표시
    document.querySelector('#editModal').classList.remove('hidden');
};

// 모달 닫기
window.closeEditModal = () => {
    document.querySelector('#editModal').classList.add('hidden');
};

// 파일 입력창의 값이 변했을 때(파일이 선택되었을 때) 실행
document.querySelector('#profileImgInput').addEventListener('change', function (e) {
    const file = e.target.files[0]; // 사용자가 선택한 첫 번째 파일

    if (file) {
        // 1. 이미지 파일인지 확인 (보안 및 오류 방지)
        if (!file.type.startsWith('image/')) {
            alert('이미지 파일만 업로드 가능합니다.');
            return;
        }

        // 2. 파일을 읽어서 미리보기 생성
        const reader = new FileReader();
        reader.onload = function (event) {
            const modalImg = document.querySelector('#modalProfileImg');
            const defaultSvg = document.querySelector('#modalDefaultSvg');

            // 이미지 소스를 읽어온 데이터로 교체
            modalImg.src = event.target.result;
            modalImg.classList.remove('hidden');
            defaultSvg.classList.add('hidden');
        };

        reader.readAsDataURL(file); // 파일을 URL 형태로 읽어옵니다.
    }
});

// 직원 정보 수정 요청 (Fetch)
window.updateMember = async () => {
    const id = document.querySelector('#editEmpId').value;
    const fileInput = document.querySelector('#profileImgInput');

    // 데이터를 담을 바구니(FormData) 생성
    const formData = new FormData();
    formData.append('name', document.querySelector('#editName').value);
    formData.append('deptId', document.querySelector('#editDept').value);
    formData.append('positionId', document.querySelector('#editPosition').value);
    formData.append('email', document.querySelector('#editEmail').value);
    formData.append('phone', document.querySelector('#editPhone').value);
    formData.append('birthDay', document.querySelector('#editBirth').value.replace(/(\d{4})-(\d{2})-(\d{2})/, '$1-$2-$3'));
    // 만약 파일이 선택되었다면 파일도 추가
    if (fileInput.files[0]) {
        formData.append('profileImg', fileInput.files[0]);
    }

    // Meta 태그에서 CSRF 정보 추출
    const token = document.querySelector('meta[name="_csrf"]')?.content;
    const header = document.querySelector('meta[name="_csrf_header"]')?.content;

    try {
        const response = await fetch(`/api/subAdmin/update/${id}`, {
            method: 'POST',
            headers: {
                [header]: token
                // 주의: FormData를 보낼 때는 'Content-Type' 헤더를 명시하지 않아야 브라우저가 자동으로 설정합니다.
            },
            body: formData
        });

        if (response.ok) {
            alert("정보가 성공적으로 수정되었습니다.");
            closeEditModal();
            loadMemberList(); 
        } else {
            const errorText = await response.text();
            alert(`수정 실패: ${errorText || '서버 오류가 발생했습니다.'}`);
        }
    } catch (error) {
        console.error("Update Error:", error);
        alert("통신 중 오류가 발생했습니다.");
    }
};


// ============================================================================
// 퇴사 처리 (자진퇴사 LEAVE / 해고 BANNED)
//   1) [퇴사] 버튼 클릭 → openResignModal() 이 #resignTypeModal 을 띄움.
//   2) 모달의 [자진퇴사] / [해고] 버튼이 confirmResign(type) 호출.
//   3) 서버에 POST /api/subAdmin/status/{id}/{LEAVE|BANNED} 로 상태 전이.
//   - "퇴사 직원 목록" 탭은 LEAVE + BANNED 를 함께 보여주도록 백엔드와 약속한다.
// ============================================================================

/**
 * 퇴사 사유 선택 모달 열기.
 * @param {HTMLElement} button 이벤트 위임으로 잡힌 [.js-resign-btn] 버튼
 */
window.openResignModal = (button) => {
    const memberId = button.dataset.memberId || '';
    const memberName = button.dataset.memberName || '';

    document.querySelector('#resignTargetId').value = memberId;
    document.querySelector('#resignTargetName').textContent = memberName || '직원';

    document.querySelector('#resignTypeModal').classList.remove('hidden');
};

/** 퇴사 사유 선택 모달 닫기 */
window.closeResignModal = () => {
    document.querySelector('#resignTypeModal').classList.add('hidden');
    document.querySelector('#resignTargetId').value = '';
};

/**
 * 모달에서 선택된 사유로 상태 전이 요청.
 *  - LEAVE  : 자진퇴사 (Status.ALLOWED 상 JOIN/ON_LEAVE → LEAVE)
 *  - BANNED : 해고     (Status.ALLOWED 상 JOIN/ON_LEAVE → BANNED)
 *  - 처리 후 현재 탭(활성/휴직)을 유지하기 위해 loadMemberList() 만 호출한다.
 * @param {('LEAVE'|'BANNED')} type 사유 코드
 */
window.confirmResign = async (type) => {
    const memberId = document.querySelector('#resignTargetId').value;
    if (!memberId) {
        alert('대상 직원 정보가 없습니다. 다시 시도해주세요.');
        closeResignModal();
        return;
    }

    const label = type === 'LEAVE' ? '자진퇴사' : '해고';
    if (!confirm(`해당 직원을 [${label}] 사유로 퇴사 처리합니다. 계속하시겠습니까?`)) return;

    const token = document.querySelector('meta[name="_csrf"]')?.content;
    const header = document.querySelector('meta[name="_csrf_header"]')?.content;

    try {
        const response = await fetch(`/api/subAdmin/status/${memberId}/${type}`, {
            method: 'POST',
            headers: {
                [header]: token
            }
        });

        if (response.ok) {
            alert(`${label} 처리되었습니다.`);
            closeResignModal();
            loadMemberList();
        } else {
            const errorText = await response.text();
            alert(`처리 실패: ${errorText || '알 수 없는 오류가 발생했습니다.'}`);
        }
    } catch (error) {
        console.error('Error during resign:', error);
        alert('서버와 통신 중 오류가 발생했습니다.');
    }
};

/**
 * 직원 휴직 처리 (JOIN → ON_LEAVE).
 * 백엔드: POST /api/subAdmin/status/{memberId}/ON_LEAVE (SubAdminApiController.changeStatus)
 * @param {number|string} memberId 대상 회원 PK
 */
window.leaveMember = async (memberId) => {
    if (!confirm("이 직원을 휴직 처리하시겠습니까?")) return;

    const token = document.querySelector('meta[name="_csrf"]')?.content;
    const header = document.querySelector('meta[name="_csrf_header"]')?.content;

    try {
        const response = await fetch(`/api/subAdmin/status/${memberId}/ON_LEAVE`, {
            method: 'POST',
            headers: {
                [header]: token
            }
        });

        if (response.ok) {
            alert("휴직 처리되었습니다.");
            loadMemberList();
        } else {
            const errorText = await response.text();
            alert(`처리 실패: ${errorText || '알 수 없는 오류가 발생했습니다.'}`);
        }
    } catch (error) {
        console.error("Error during leave:", error);
        alert("서버와 통신 중 오류가 발생했습니다.");
    }
};

/**
 * 직원 복귀 처리 (ON_LEAVE → JOIN).
 * 백엔드: POST /api/subAdmin/status/{memberId}/JOIN (SubAdminApiController.changeStatus)
 *  ※ 동작 전제: Status enum 의 ON_LEAVE 전이 허용 목록에 JOIN 이 추가되어 있어야 한다.
 * @param {number|string} memberId 대상 회원 PK
 */
window.restoreMember = async (memberId) => {
    if (!confirm("이 직원을 복귀(활성) 처리하시겠습니까?")) return;

    const token = document.querySelector('meta[name="_csrf"]')?.content;
    const header = document.querySelector('meta[name="_csrf_header"]')?.content;

    try {
        const response = await fetch(`/api/subAdmin/status/${memberId}/JOIN`, {
            method: 'POST',
            headers: {
                [header]: token
            }
        });

        if (response.ok) {
            alert("복귀 처리되었습니다.");
            loadMemberList();
        } else {
            const errorText = await response.text();
            alert(`처리 실패: ${errorText || '알 수 없는 오류가 발생했습니다.'}`);
        }
    } catch (error) {
        console.error("Error during restore:", error);
        alert("서버와 통신 중 오류가 발생했습니다.");
    }
};

/**
 * 상태별 탭(JOIN / ON_LEAVE / LEAVE) 전환 핸들러.
 *  1) hidden input(#statusInput) 값을 갱신해서 다음 fetch 요청에 status 가 실린다.
 *  2) 통신을 기다리지 않고 즉시 활성 탭 시각효과를 갈아끼워 클릭 응답성을 높인다.
 *  3) loadMemberList() 호출 시 fragment 가 새 status 기준으로 재렌더링된다.
 * @param {('JOIN'|'ON_LEAVE'|'LEAVE')} status 전환할 상태 값
 */
window.selectStatusTab = (status) => {
    const statusInput = document.getElementById('statusInput');
    if (statusInput) statusInput.value = status;

    document.querySelectorAll('.js-status-tab').forEach((tab) => {
        const isActive = tab.dataset.status === status;
        tab.classList.toggle('text-primary', isActive);
        tab.classList.toggle('font-bold', isActive);
        tab.classList.toggle('border-b-2', isActive);
        tab.classList.toggle('border-primary', isActive);
        tab.classList.toggle('text-gray-400', !isActive);
        tab.classList.toggle('dark:text-gray-300', !isActive);
    });

    loadMemberList();
};

/**
 * 직원 목록을 AJAX로 로드하는 공통 함수
 */
function loadMemberList() {
    const form = document.getElementById('filterForm');
    if (!form) return;

    // 폼 안의 모든 데이터(deptId, name, sort 등)를 가져옴
    const formData = new FormData(form);
    const params = new URLSearchParams(formData);

    // AJAX 요청 URL 생성
    const url = `${form.action}?${params.toString()}`;

    fetch(url, {
        method: 'GET',
        headers: {
            'X-Requested-With': 'XMLHttpRequest'
        }
    })
        .then(response => {
            if (!response.ok) throw new Error('네트워크 응답에 문제가 있습니다.');
            return response.text();
        })
        .then(html => {
            const container = document.getElementById('memberListContainer');
            if (container) {
                container.innerHTML = html;
                initCustomSelects();

                // 카드 헤더가 상태별 탭(JOIN / ON_LEAVE / LEAVE)으로 교체된 이후에는
                // 별도의 제목 갱신 로직이 필요 없다. 부서 필터는 검색 폼의 select 가 그대로 표현한다.
                // 버튼(수정/삭제 등) 이벤트는 document 레벨 위임이므로 별도 재초기화 불필요.
            }
        })
        .catch(error => {
            console.error('AJAX 로드 실패:', error);
            alert('목록을 불러오는 중 오류가 발생했습니다.');
        });
}

/**
 * 정렬 버튼 클릭 시 호출
 */
function submitWithSort(sortValue) {
    const sortInput = document.getElementById('sortInput');
    if (sortInput) {
        sortInput.value = sortValue;
        loadMemberList(); // 정렬 값 세팅 후 AJAX 호출
    }
}