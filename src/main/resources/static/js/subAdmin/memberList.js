const customSelectMap = new Map();

function closeAllCustomSelects() {
    customSelectMap.forEach(({ menu, trigger, container, arrow }) => {
        menu.classList.add('hidden');
        trigger.setAttribute('aria-expanded', 'false');
        arrow?.classList.remove('is-open');
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
        button.classList.toggle('is-selected', button.dataset.value === select.value);
    });
}

function createCustomSelect(select) {
    if (!select || select.dataset.customized === 'true') return;

    const container = select.parentElement;
    const wrapper = document.createElement('div');
    wrapper.className = 'relative';

    const trigger = document.createElement('button');
    trigger.type = 'button';
    trigger.className = 'form-combobox-trigger';
    trigger.setAttribute('aria-haspopup', 'listbox');
    trigger.setAttribute('aria-expanded', 'false');

    const triggerText = document.createElement('span');
    triggerText.className = 'truncate';

    const arrow = document.createElement('span');
    arrow.className = 'form-combobox-arrow';
    arrow.setAttribute('aria-hidden', 'true');
    arrow.textContent = '▾';

    trigger.appendChild(triggerText);
    trigger.appendChild(arrow);

    const menu = document.createElement('div');
    menu.className = 'form-combobox-panel hidden';
    menu.setAttribute('role', 'listbox');

    Array.from(select.options).forEach((option) => {
        const item = document.createElement('button');
        item.type = 'button';
        item.dataset.value = option.value;
        item.disabled = option.disabled;
        item.className = 'form-combobox-option';
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
            arrow.classList.add('is-open');
        }
    });

    select.classList.add('hidden');
    select.insertAdjacentElement('afterend', wrapper);
    wrapper.appendChild(trigger);
    wrapper.appendChild(menu);
    select.dataset.customized = 'true';

    customSelectMap.set(select.id, { select, trigger, triggerText, menu, container, arrow });
    select.addEventListener('change', () => syncCustomSelect(select.id));
    syncCustomSelect(select.id);
}

function initCustomSelects() {
    document.querySelectorAll('.js-custom-select').forEach((select) => createCustomSelect(select));
}

/**
 * 인사이동 직급 dropdown 에서 부여 불가능한 직급을 제거.
 *  - 대표(role=ADMIN 또는 시스템 직급) 옵션은 모두에게 제거 — 회사 대표 임명은 MASTER 도구에서.
 *  - ADMIN/MASTER 가 아닌 사용자는 본인보다 "높은" 직급 옵션만 제거 (위계 보호).
 *    동등 직급은 허용 — 부장이 사원을 부장으로 승진시키는 정상 인사이동을 막지 않도록.
 *  - native select 의 option 자체를 제거해서 커스텀 콤보 빌드 이전에 정리 (createCustomSelect 가 option 을 복사하므로 순서가 중요).
 */
function pruneReassignablePositions() {
    const select = document.querySelector('#reassignPositionSelect');
    if (!select) return;
    const body = document.body;
    const isAdminOrMaster = body.dataset.isAdminOrMaster === 'true';
    const myLevelRaw = body.dataset.myPositionLevel;
    const myLevel = myLevelRaw === '' || myLevelRaw === undefined ? null : Number(myLevelRaw);
    Array.from(select.options).forEach((opt) => {
        if (!opt.value) return; // 안내용 "직급 선택" 은 유지
        const role = opt.dataset.role || '';
        const system = opt.dataset.system === 'true';
        if (role === 'ADMIN' || system) {
            opt.remove();
            return;
        }
        if (!isAdminOrMaster && myLevel != null && !Number.isNaN(myLevel)) {
            const targetLevel = Number(opt.dataset.level);
            if (!Number.isNaN(targetLevel) && targetLevel > myLevel) {
                opt.remove();
            }
        }
    });
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

    pruneReassignablePositions();
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

    // 수정 모달: 배경(딤) 클릭 시 닫기 — calendar 의 .modal-close-btn 과 동일
    document.querySelectorAll('#editModal .modal-close-btn').forEach((btn) => {
        btn.addEventListener('click', closeEditModal);
    });

    // 퇴사 사유 모달: 배경(딤) 클릭 시 닫기
    document.querySelectorAll('#resignTypeModal .modal-close-btn').forEach((btn) => {
        btn.addEventListener('click', closeResignModal);
    });
});


// 모달 열기 및 데이터 채우기
window.openEditModal = (button) => {
    document.querySelector('#editEmpId').value = button.dataset.memberId || '';
    document.querySelector('#editName').value = button.dataset.memberName || '';
    // 부서/직급은 readonly 표시. hidden(#editDept,#editPosition) 에는 id 를, display 인풋에는 이름을 채운다.
    document.querySelector('#editDept').value = button.dataset.deptId || '';
    document.querySelector('#editPosition').value = button.dataset.positionId || '';
    const deptDisplay = document.querySelector('#editDeptDisplay');
    const positionDisplay = document.querySelector('#editPositionDisplay');
    // input → span 으로 바뀌었으므로 textContent 사용.
    if (deptDisplay) deptDisplay.textContent = button.dataset.deptName || '미지정';
    if (positionDisplay) positionDisplay.textContent = button.dataset.positionName || '미지정';
    document.querySelector('#editEmail').value = button.dataset.email || '';
    document.querySelector('#editPhone').value = button.dataset.phone || '';
    document.querySelector('#editBirth').value = button.dataset.birth || '';
    const hireEl = document.querySelector('#editHire');
    if (hireEl) hireEl.value = button.dataset.hire || '';

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
    const hireInput = document.querySelector('#editHire');
    formData.append('hireDate', hireInput ? hireInput.value : '');
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
 * 휴직 모달 열기 — 사유 + 시작일 + 복귀예정일을 입력 받기 위해 dialog 를 띄운다.
 * (기존 즉시 휴직 처리는 모달 제출 시 submitLeaveModal() 에서 새 API 호출로 대체.)
 * @param {number|string} memberId 대상 회원 PK
 * @param {string} memberName 표시용 이름
 */
window.openLeaveModal = (memberId, memberName) => {
    const modal = document.getElementById('leaveModal');
    if (!modal) return;
    document.getElementById('leaveTargetId').value = String(memberId);
    document.getElementById('leaveTargetName').textContent = memberName || '-';
    document.getElementById('leaveReasonInput').value = '';
    // 기본값: 오늘 / 한 달 뒤
    const today = new Date();
    const monthLater = new Date(today.getTime());
    monthLater.setMonth(monthLater.getMonth() + 1);
    document.getElementById('leaveStartInput').value = toIsoDate(today);
    document.getElementById('leaveReturnInput').value = toIsoDate(monthLater);
    openModalElement(modal);
};

/** 휴직 모달 닫기. */
window.closeLeaveModal = () => {
    closeModalElement(document.getElementById('leaveModal'));
};

/**
 * 휴직 모달 제출 — POST /api/subAdmin/leave/{memberId}.
 *  - 사유는 빈 문자열 허용(백엔드가 trim 후 보관).
 *  - 시작일/복귀일은 둘 다 필수, 복귀일 >= 시작일.
 */
window.submitLeaveModal = async () => {
    const memberId = document.getElementById('leaveTargetId').value;
    const reason = document.getElementById('leaveReasonInput').value;
    const startDate = document.getElementById('leaveStartInput').value;
    const expectedReturnDate = document.getElementById('leaveReturnInput').value;

    if (!memberId) return;
    if (!startDate || !expectedReturnDate) {
        alert('휴직 시작일과 복귀 예정일을 모두 입력하세요.');
        return;
    }
    if (expectedReturnDate < startDate) {
        alert('복귀 예정일은 휴직 시작일 이후여야 합니다.');
        return;
    }

    const token = document.querySelector('meta[name="_csrf"]')?.content;
    const header = document.querySelector('meta[name="_csrf_header"]')?.content;

    try {
        const response = await fetch(`/api/subAdmin/leave/${memberId}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                [header]: token,
            },
            body: JSON.stringify({ reason, startDate, expectedReturnDate }),
        });

        if (response.ok) {
            closeModalElement(document.getElementById('leaveModal'));
            alert('휴직 처리되었습니다.');
            loadMemberList();
        } else {
            const msg = await window.getApiErrorMessage(response, '휴직 처리에 실패했습니다.');
            alert(msg);
        }
    } catch (error) {
        console.error('Error during leave:', error);
        alert('서버와 통신 중 오류가 발생했습니다.');
    }
};

/**
 * 복귀 처리 통합 모달 열기 — [즉시 복귀] 또는 [복귀일 변경] 두 액션 모두 제공.
 * @param {number|string} memberId
 * @param {string} memberName
 * @param {string} currentReturnDate yyyy-MM-dd
 * @param {string} startDate yyyy-MM-dd — 복귀일 변경 input 의 min 으로 사용
 */
window.openRestoreModal = (memberId, memberName, currentReturnDate, startDate) => {
    const modal = document.getElementById('restoreModal');
    if (!modal) return;
    document.getElementById('restoreTargetId').value = String(memberId);
    document.getElementById('restoreTargetName').textContent = memberName || '-';
    document.getElementById('restoreStartDate').value = startDate || '';
    const input = document.getElementById('restoreReturnInput');
    input.value = currentReturnDate || '';
    if (startDate) input.min = startDate;
    openModalElement(modal);
};

/** 복귀 모달 닫기. */
window.closeRestoreModal = () => {
    closeModalElement(document.getElementById('restoreModal'));
};

/** 즉시 복귀 — 기존 restoreMember(memberId) 와 동일 흐름. 확인 후 JOIN 으로 변경. */
window.submitImmediateRestore = async () => {
    const memberId = document.getElementById('restoreTargetId').value;
    if (!memberId) return;
    if (!confirm('이 직원을 즉시 복귀(활성) 처리하시겠습니까?')) return;

    const token = document.querySelector('meta[name="_csrf"]')?.content;
    const header = document.querySelector('meta[name="_csrf_header"]')?.content;

    try {
        const response = await fetch(`/api/subAdmin/status/${memberId}/JOIN`, {
            method: 'POST',
            headers: { [header]: token },
        });
        if (response.ok) {
            closeModalElement(document.getElementById('restoreModal'));
            alert('복귀 처리되었습니다.');
            loadMemberList();
        } else {
            const msg = await window.getApiErrorMessage(response, '복귀 처리에 실패했습니다.');
            alert(msg);
        }
    } catch (error) {
        console.error('Error during immediate restore:', error);
        alert('서버와 통신 중 오류가 발생했습니다.');
    }
};

/**
 * 복귀일 변경 제출 — POST /api/subAdmin/leave/{memberId}/extend.
 * leaveExtended=true 로 마킹되어 카드에 '연장' 배지가 표시된다.
 */
window.submitExtendReturnDate = async () => {
    const memberId = document.getElementById('restoreTargetId').value;
    const expectedReturnDate = document.getElementById('restoreReturnInput').value;
    const startDate = document.getElementById('restoreStartDate').value;

    if (!memberId) return;
    if (!expectedReturnDate) {
        alert('새 복귀 예정일을 선택하세요.');
        return;
    }
    if (startDate && expectedReturnDate < startDate) {
        alert('복귀 예정일은 휴직 시작일 이후여야 합니다.');
        return;
    }

    const token = document.querySelector('meta[name="_csrf"]')?.content;
    const header = document.querySelector('meta[name="_csrf_header"]')?.content;

    try {
        const response = await fetch(`/api/subAdmin/leave/${memberId}/extend`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                [header]: token,
            },
            body: JSON.stringify({ expectedReturnDate }),
        });

        if (response.ok) {
            closeModalElement(document.getElementById('restoreModal'));
            alert('복귀 예정일이 변경되었습니다.');
            loadMemberList();
        } else {
            const msg = await window.getApiErrorMessage(response, '복귀일 변경에 실패했습니다.');
            alert(msg);
        }
    } catch (error) {
        console.error('Error during extendLeave:', error);
        alert('서버와 통신 중 오류가 발생했습니다.');
    }
};

/** Date → 'yyyy-MM-dd' */
function toIsoDate(d) {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
}

/** 공용 모달 열기: hidden 제거 + 배경 클릭 닫기. */
function openModalElement(modal) {
    if (!modal) return;
    modal.classList.remove('hidden');
    modal.querySelectorAll('.modal-close-btn').forEach((el) => {
        if (!el.dataset.bound) {
            el.addEventListener('click', () => closeModalElement(modal));
            el.dataset.bound = '1';
        }
    });
}

/** 공용 모달 닫기. */
function closeModalElement(modal) {
    if (!modal) return;
    modal.classList.add('hidden');
}

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
    // 인사이동 모드는 JOIN 탭 한정. 다른 탭으로 이동 시 모드 OFF.
    if (isReassignMode && status !== 'JOIN') {
        isReassignMode = false;
        applyReassignModeUI();
        document.querySelectorAll('.js-reassign-member-check').forEach((c) => { c.checked = false; });
        updateReassignSelectedCount();
    }

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
 * 직원 목록을 AJAX로 로드하는 공통 함수.
 *  ※ "퇴사 직원 목록" 탭(status=LEAVE) 은 자진퇴사(LEAVE) + 해고(BANNED) 를 함께 조회한다.
 *    백엔드는 List<Status> 를 받아 m.status IN :statuses 절로 처리하므로
 *    status 파라미터를 두 번 append 하면 둘 다 결과에 포함된다.
 */
function loadMemberList() {
    const form = document.getElementById('filterForm');
    if (!form) return;

    // 폼 안의 모든 데이터(deptId, name, sort 등)를 가져옴
    const formData = new FormData(form);
    const params = new URLSearchParams(formData);

    // 퇴사 탭은 항상 LEAVE + BANNED 두 가지 상태를 함께 조회한다.
    //  ※ Spring 의 @RequestParam List<Status> 는 multiple-param(`?status=A&status=B`) 형식과
    //    콤마 분리(`?status=A,B`) 형식 둘 다 받지만, 일부 환경에서 multiple-param 이 첫 값만
    //    binding 되는 케이스가 있어 콤마 분리 형식으로 보낸다.
    if (params.get('status') === 'LEAVE') {
        params.set('status', 'LEAVE,BANNED');
    }

    // AJAX 요청 URL 생성
    const url = `${form.action}?${params.toString()}`;

    fetch(url, {
        method: 'GET',
        headers: {
            'X-Requested-With': 'XMLHttpRequest'
        }
    })
        .then(async response => {
            if (!response.ok) throw new Error(await window.getApiErrorMessage(response, '네트워크 응답에 문제가 있습니다.'));
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

                // fragment 가 갈아끼워지면 체크박스가 새로 그려지므로 인사이동 모드 가시성을 다시 적용한다.
                applyReassignModeUI();
                updateReassignSelectedCount();
            }
        })
        .catch(error => {
            console.error('AJAX 로드 실패:', error);
            alert(error?.message || '목록을 불러오는 중 오류가 발생했습니다.');
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

// ============================================================================
// 인사이동: 다수 회원 일괄 부서/직급 변경
//  - 토글 버튼 [인사이동] 클릭 → 체크박스 + 부서/직급 선택 패널 노출
//  - [등록] 버튼 → 선택된 회원들에 대해 일괄 reassign API 호출
//  - 활성(JOIN) 탭에서만 의미가 있으므로 휴직/퇴사 탭에서는 토글 후에도 체크박스가 없다 (체크박스 자체가 렌더링되지 않음).
// ============================================================================

let isReassignMode = false;

/**
 * 인사이동 모드 토글.
 *  - 카드 행/카드 헤더의 .js-reassign-toggle 요소들의 hidden 클래스를 일괄 토글.
 *  - 하단 부서/직급 선택 패널(#reassignPanel) 도 같이 토글.
 *  - OFF 전환 시 모든 체크박스 해제 + 카운트 0 으로 리셋.
 */
window.toggleReassignMode = () => {
    isReassignMode = !isReassignMode;
    applyReassignModeUI();

    if (!isReassignMode) {
        // OFF 시 상태 초기화
        document.querySelectorAll('.js-reassign-member-check').forEach((c) => { c.checked = false; });
        const all = document.querySelector('#reassignSelectAll');
        if (all) all.checked = false;
        updateReassignSelectedCount();
    }
};

/** 현재 isReassignMode 값을 DOM 에 반영. fragment 갱신 후에도 재호출해서 체크박스 가시성을 유지한다. */
function applyReassignModeUI() {
    document.querySelectorAll('.js-reassign-toggle').forEach((el) => {
        el.classList.toggle('hidden', !isReassignMode);
    });
    const panel = document.querySelector('#reassignPanel');
    if (panel) panel.classList.toggle('hidden', !isReassignMode);

    const btn = document.querySelector('#reassignToggleBtn');
    if (btn) {
        btn.textContent = isReassignMode ? '취소' : '인사이동';
        btn.classList.toggle('bg-indigo-400', !isReassignMode);
        btn.classList.toggle('hover:bg-indigo-500', !isReassignMode);
        btn.classList.toggle('bg-gray-300', isReassignMode);
        btn.classList.toggle('hover:bg-gray-400', isReassignMode);
        btn.classList.toggle('text-white', !isReassignMode);
        btn.classList.toggle('text-gray-700', isReassignMode);
    }
}

/** 전체선택 체크박스 클릭 핸들러. 현재 화면에 보이는 모든 회원 체크박스를 동일 상태로. */
window.onReassignSelectAll = (master) => {
    const checked = !!master.checked;
    document.querySelectorAll('.js-reassign-member-check').forEach((c) => { c.checked = checked; });
    updateReassignSelectedCount();
};

/** 개별 회원 체크박스 클릭 시: 전체선택 체크박스 상태 동기화 + 카운트 갱신. */
window.onReassignMemberCheck = () => {
    const all = document.querySelectorAll('.js-reassign-member-check');
    const checkedCount = Array.from(all).filter((c) => c.checked).length;
    const master = document.querySelector('#reassignSelectAll');
    if (master) master.checked = all.length > 0 && checkedCount === all.length;
    updateReassignSelectedCount();
};

/** 선택된 회원 수 표시 텍스트 갱신. */
function updateReassignSelectedCount() {
    const label = document.querySelector('#reassignSelectedCount');
    if (!label) return;
    const count = document.querySelectorAll('.js-reassign-member-check:checked').length;
    label.textContent = `선택 ${count}명`;
}

/**
 * [등록] 버튼: 선택된 회원들의 부서/직급을 일괄 변경.
 *  - 부서/직급 둘 다 비어있으면 진행 불가.
 *  - 둘 중 하나만 선택된 경우 그 한 쪽만 일괄 변경 (백엔드 reassignMembers 가 null 허용).
 */
window.submitReassign = async () => {
    const selected = Array.from(document.querySelectorAll('.js-reassign-member-check:checked'))
        .map((c) => c.value)
        .filter((v) => v);
    if (selected.length === 0) {
        alert('이동 대상 직원을 선택하세요.');
        return;
    }

    const deptId = document.querySelector('#reassignDeptSelect')?.value || '';
    const positionId = document.querySelector('#reassignPositionSelect')?.value || '';
    if (!deptId && !positionId) {
        alert('변경할 부서 또는 직급을 선택하세요.');
        return;
    }

    if (!confirm(`선택된 ${selected.length}명의 부서/직급을 변경합니다. 계속하시겠습니까?`)) return;

    const token = document.querySelector('meta[name="_csrf"]')?.content;
    const header = document.querySelector('meta[name="_csrf_header"]')?.content;

    const formData = new URLSearchParams();
    selected.forEach((id) => formData.append('memberIds', id));
    if (deptId) formData.append('deptId', deptId);
    if (positionId) formData.append('positionId', positionId);

    try {
        const response = await fetch('/api/subAdmin/reassign', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                [header]: token
            },
            body: formData.toString()
        });

        if (response.ok) {
            alert('인사이동이 완료되었습니다.');
            // 모드는 유지하되 선택 상태만 초기화 (연속 작업 편의)
            document.querySelectorAll('.js-reassign-member-check').forEach((c) => { c.checked = false; });
            const all = document.querySelector('#reassignSelectAll');
            if (all) all.checked = false;
            const deptSel = document.querySelector('#reassignDeptSelect');
            const posSel = document.querySelector('#reassignPositionSelect');
            if (deptSel) { deptSel.value = ''; deptSel.dispatchEvent(new Event('change', { bubbles: true })); }
            if (posSel) { posSel.value = ''; posSel.dispatchEvent(new Event('change', { bubbles: true })); }
            updateReassignSelectedCount();
            loadMemberList();
        } else {
            const errorText = await response.text();
            alert(`인사이동 실패: ${errorText || '서버 오류가 발생했습니다.'}`);
        }
    } catch (error) {
        console.error('Reassign Error:', error);
        alert('서버와 통신 중 오류가 발생했습니다.');
    }
};

// ============================================================================
// 비밀번호 초기화 (수정 모달 → 보안 섹션 → [비밀번호 초기화])
//   1) 수정 모달 안 #editEmpId 의 회원 ID 를 사용해 POST /api/subAdmin/{id}/reset-password.
//   2) 응답으로 받은 평문 임시 비번을 #tempPasswordModal 에 노출 (1회만).
//   3) [복사] 버튼은 클립보드에 임시 비번을 복사한다.
//  ※ MASTER 회사 대표 비번 초기화와 동일한 단방향 흐름 — 모달 닫으면 다시 못 봄.
// ============================================================================

/**
 * 직원 비밀번호 초기화.
 *  - 수정 모달이 열려있는 상태에서만 동작 (대상 회원 ID 를 #editEmpId 에서 가져옴).
 */
window.resetMemberPassword = async () => {
    const memberId = document.querySelector('#editEmpId')?.value;
    if (!memberId) {
        alert('대상 직원 정보가 없습니다. 다시 시도해주세요.');
        return;
    }
    if (!confirm('이 직원의 비밀번호를 임시 비밀번호로 초기화합니다.\n계속하시겠습니까?')) return;

    const token = document.querySelector('meta[name="_csrf"]')?.content;
    const header = document.querySelector('meta[name="_csrf_header"]')?.content;
    const headers = {};
    if (token && header) headers[header] = token;

    try {
        const response = await fetch(`/api/subAdmin/${memberId}/reset-password`, {
            method: 'POST',
            headers,
        });

        let payload = null;
        try { payload = await response.json(); } catch (_) { /* no body */ }

        if (response.ok && payload?.success && payload?.tempPassword) {
            // 수정 모달은 닫고 결과(임시 비번) 모달만 보여 준다 — 시각적 흐름 분리.
            closeEditModal();
            openTempPasswordModal(payload.tempPassword);
        } else {
            alert(payload?.message || '비밀번호 초기화에 실패했습니다.');
        }
    } catch (error) {
        console.error('Reset password error:', error);
        alert('통신 중 오류가 발생했습니다.');
    }
};

/** 임시 비번 결과 모달 열기 — 화면에 비번 노출. */
function openTempPasswordModal(tempPassword) {
    const modal = document.querySelector('#tempPasswordModal');
    const view = document.querySelector('#tempPasswordValue');
    if (!modal || !view) return;
    view.textContent = tempPassword;
    modal.classList.remove('hidden');
}

/** 임시 비번 결과 모달 닫기 — 표시값 초기화로 노출 흔적 제거. */
window.closeTempPasswordModal = () => {
    const modal = document.querySelector('#tempPasswordModal');
    const view = document.querySelector('#tempPasswordValue');
    if (view) view.textContent = '--------';
    modal?.classList.add('hidden');
};

/** 클립보드 복사 — navigator.clipboard 우선, 실패 시 execCommand fallback. */
window.copyTempPassword = async () => {
    const view = document.querySelector('#tempPasswordValue');
    const text = view?.textContent?.trim();
    if (!text) return;
    try {
        if (navigator.clipboard && window.isSecureContext) {
            await navigator.clipboard.writeText(text);
        } else {
            // 일부 사내망/구버전 브라우저용 fallback
            const tmp = document.createElement('textarea');
            tmp.value = text;
            tmp.style.position = 'fixed';
            tmp.style.opacity = '0';
            document.body.appendChild(tmp);
            tmp.select();
            document.execCommand('copy');
            document.body.removeChild(tmp);
        }
        alert('임시 비밀번호가 복사되었습니다.');
    } catch (error) {
        console.error('Clipboard copy error:', error);
        alert('복사에 실패했습니다. 직접 선택해서 복사해주세요.');
    }
};

// 임시 비번 모달: 배경(딤) 클릭 시 닫기 — 다른 모달들과 동일 패턴.
document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('#tempPasswordModal .modal-close-btn').forEach((btn) => {
        btn.addEventListener('click', closeTempPasswordModal);
    });
});