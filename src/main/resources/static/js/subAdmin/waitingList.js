(() => {
    const customSelectMap = new Map();

    const getCsrfToken = () => {
        const token = document.querySelector('meta[name="_csrf"]')?.content;
        const header = document.querySelector('meta[name="_csrf_header"]')?.content;
        return { token, header };
    };

    const closeAllCustomSelects = () => {
        customSelectMap.forEach(({ menu, trigger, container, arrow }) => {
            menu.classList.add('hidden');
            trigger.setAttribute('aria-expanded', 'false');
            arrow?.classList.remove('is-open');
            if (container) {
                container.style.zIndex = '';
            }
        });
    };

    const syncCustomSelect = (selectId) => {
        const ui = customSelectMap.get(selectId);
        if (!ui) return;

        const { select, triggerText, menu } = ui;
        const selectedOption = select.options[select.selectedIndex];
        triggerText.textContent = selectedOption ? selectedOption.textContent : '선택하세요';

        menu.querySelectorAll('button[data-value]').forEach((button) => {
            button.classList.toggle('is-selected', button.dataset.value === select.value);
        });
    };

    const createCustomSelect = (select) => {
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
    };

    const initCustomSelects = () => {
        document.querySelectorAll('.js-custom-select').forEach((select) => createCustomSelect(select));
    };

    /**
     * 회원 가입 승인 처리
     */
    const approveMember = async (memberId) => {
        // 머스테치에 맞춰 positionId만 가져옴 (이미지 디자인상 부서 선택이 없다면)
        const positionElement = document.getElementById(`position-${memberId}`);
        const positionId = positionElement ? positionElement.value : null;

        // deptId도 가져옴
        const deptElement = document.getElementById(`dept-${memberId}`);
        const deptId = deptElement ? deptElement.value : null;

        if (!positionId) {
            alert("직급을 선택해주세요.");
            return;
        }

        if (!deptId) {
            alert("부서를 선택해주세요.");
            return;
        }

        if (!confirm("해당 회원의 가입을 승인하시겠습니까?")) return;

        const { token, header } = getCsrfToken();
        const params = new URLSearchParams();
        // 컨트롤러 요구사항에 따라 필요시 추가 (현재 디자인상으론 position만 있음)
        params.append('positionId', positionId);
        params.append('deptId', deptId);
        try {
            const response = await fetch(`/api/subAdmin/accept/${memberId}`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    [header]: token
                },
                body: params
            });

            if (response.redirected) {
                window.location.href = response.url;
                return;
            }

            if (response.ok) {
                alert("성공적으로 승인되었습니다.");
                location.reload();
            } else {
                alert(await window.getApiErrorMessage(response, "승인 처리 중 오류가 발생했습니다."));
            }
        } catch (error) {
            console.error('Error:', error);
            alert("서버 통신 중 오류가 발생했습니다.");
        }
    };

    /**
     * 회원 가입 반려(거절) 처리
     */
    const rejectMember = async (memberId) => {
        if (!confirm("가입 신청을 반려하시겠습니까?")) return;

        const { token, header } = getCsrfToken();

        try {
            const response = await fetch(`/api/subAdmin/status/${memberId}/REJECT`, {
                method: 'POST',
                headers: {
                    [header]: token
                }
            });

            if (response.redirected) {
                window.location.href = response.url;
                return;
            }

            if (response.ok) {
                alert("반려 처리가 완료되었습니다.");
                location.reload();
            } else {
                alert(await window.getApiErrorMessage(response, "반려 처리 중 오류 발생"));
            }
        } catch (error) {
            console.error('Error:', error);
            alert("처리 중 오류가 발생했습니다.");
        }
    };

    window.approveMember = approveMember;
    window.rejectMember = rejectMember;
    document.addEventListener('DOMContentLoaded', () => {
        initCustomSelects();
        document.addEventListener('click', () => closeAllCustomSelects());
    });
})();