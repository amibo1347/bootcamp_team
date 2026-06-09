/**
 * 전자결재 위저드 — DESIGN_RULES 5-2 커스텀 콤보박스
 * - 숨은 native select + form-combobox-trigger/panel (managingBoard·attendanceList 패턴)
 * - 열린 옵션 목록까지 동일 톤(indigo-50 선택 행) 적용
 */

/** @type {Map<HTMLSelectElement, { wrapper: HTMLElement, trigger: HTMLButtonElement, triggerText: HTMLElement, menu: HTMLElement, arrow: HTMLElement }>} */
const comboboxUiMap = new Map();

let documentClickBound = false;

/**
 * 열린 콤보박스를 모두 닫는다.
 */
function closeAllApprovalComboboxes() {
  comboboxUiMap.forEach((ui) => {
    ui.menu.classList.add('hidden');
    ui.trigger.setAttribute('aria-expanded', 'false');
    ui.arrow.classList.remove('is-open');
    const parent = ui.wrapper.parentElement;
    if (parent instanceof HTMLElement && parent.dataset.comboboxElevated === 'true') {
      parent.style.zIndex = '';
      delete parent.dataset.comboboxElevated;
    }
  });
}

/**
 * 트리거 라벨·옵션 선택 상태를 native select 와 맞춘다.
 * @param {HTMLSelectElement} select
 */
function syncApprovalCombobox(select) {
  const ui = comboboxUiMap.get(select);
  if (!ui) return;
  const selected = select.options[select.selectedIndex];
  ui.triggerText.textContent = selected?.textContent?.trim() || '선택하세요';
  ui.menu.querySelectorAll('[data-combobox-value]').forEach((btn) => {
    btn.classList.toggle('is-selected', btn.dataset.comboboxValue === select.value);
  });
}

/**
 * 패널 옵션 버튼을 select.options 기준으로 다시 만든다.
 * @param {HTMLElement} menu
 * @param {HTMLSelectElement} select
 */
function buildApprovalComboboxMenu(menu, select) {
  menu.innerHTML = '';
  Array.from(select.options).forEach((option) => {
    const item = document.createElement('button');
    item.type = 'button';
    item.dataset.comboboxValue = option.value;
    item.disabled = option.disabled;
    item.className = 'form-combobox-option';
    item.textContent = option.textContent;
    item.addEventListener('click', (event) => {
      event.stopPropagation();
      select.value = option.value;
      select.dispatchEvent(new Event('change', { bubbles: true }));
      closeAllApprovalComboboxes();
    });
    menu.appendChild(item);
  });
}

/**
 * 바깥 클릭 시에만 패널을 닫는다.
 */
function bindDocumentClickClose() {
  if (documentClickBound) return;
  documentClickBound = true;
  document.addEventListener('click', (event) => {
    const target = event.target;
    if (!(target instanceof Node)) return;
    for (const ui of comboboxUiMap.values()) {
      if (ui.wrapper.contains(target)) return;
    }
    closeAllApprovalComboboxes();
  });
}

/**
 * native select 위에 커스텀 콤보박스 UI를 붙인다. (이미 붙어 있으면 목록만 갱신)
 * @param {HTMLSelectElement|null|undefined} select
 */
export function mountApprovalCombobox(select) {
  if (!(select instanceof HTMLSelectElement)) return;

  bindDocumentClickClose();

  if (select.dataset.approvalCombobox === 'true') {
    const ui = comboboxUiMap.get(select);
    if (ui) {
      buildApprovalComboboxMenu(ui.menu, select);
      syncApprovalCombobox(select);
    }
    return;
  }

  select.classList.add('sr-only', 'pointer-events-none');
  select.tabIndex = -1;
  select.setAttribute('aria-hidden', 'true');

  const wrapper = document.createElement('div');
  wrapper.className = 'relative w-full';
  wrapper.dataset.approvalComboboxRoot = 'true';

  const trigger = document.createElement('button');
  trigger.type = 'button';
  trigger.className = 'form-combobox-trigger w-full';
  trigger.setAttribute('aria-haspopup', 'listbox');
  trigger.setAttribute('aria-expanded', 'false');

  const triggerText = document.createElement('span');
  triggerText.className = 'min-w-0 flex-1 truncate text-left';

  const arrow = document.createElement('span');
  arrow.className = 'form-combobox-arrow';
  arrow.setAttribute('aria-hidden', 'true');
  arrow.textContent = '▾';

  trigger.appendChild(triggerText);
  trigger.appendChild(arrow);

  const menu = document.createElement('div');
  menu.className = 'form-combobox-panel hidden';
  menu.dataset.approvalComboboxMenu = 'true';
  menu.setAttribute('role', 'listbox');

  select.insertAdjacentElement('afterend', wrapper);
  wrapper.appendChild(trigger);
  wrapper.appendChild(menu);

  trigger.addEventListener('click', (event) => {
    event.preventDefault();
    event.stopPropagation();
    const isOpen = !menu.classList.contains('hidden');
    closeAllApprovalComboboxes();
    if (!isOpen) {
      const parent = wrapper.parentElement;
      if (parent instanceof HTMLElement) {
        parent.dataset.comboboxElevated = 'true';
        parent.style.zIndex = '9999';
      }
      menu.classList.remove('hidden');
      trigger.setAttribute('aria-expanded', 'true');
      arrow.classList.add('is-open');
    }
  });

  menu.addEventListener('click', (event) => {
    event.stopPropagation();
  });

  select.dataset.approvalCombobox = 'true';
  comboboxUiMap.set(select, { wrapper, trigger, triggerText, menu, arrow });

  select.addEventListener('change', () => syncApprovalCombobox(select));
  buildApprovalComboboxMenu(menu, select);
  syncApprovalCombobox(select);
}
