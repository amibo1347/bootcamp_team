/**
 * 지출결의서(EXPENSE) 양식 유틸
 * - 금액 / 분류 / 지출일 / 상세 필드. serialize/validate/reset 트리오.
 * - 필드 HTML 은 mountExpenseFormFields() 가 한 번 inject (form-registry 가 호출).
 */

import { getValue, attachShowPickerOnClick } from './form-utils.js';

const SECTION_HTML = `
  <div id="approval-expense-fields" data-approval-form-section="EXPENSE"
       class="hidden space-y-3 rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-white/[0.03]">
    <p class="text-xs font-semibold text-gray-700 dark:text-gray-300">지출 정보</p>
    <div class="grid gap-3 sm:grid-cols-2">
      <div>
        <label for="expense-amount" class="mb-1 block text-xs text-gray-600 dark:text-gray-400">금액 (원)</label>
        <input id="expense-amount" type="number" min="0" step="1" placeholder="예: 50000"
          class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm dark:border-gray-700 dark:bg-gray-900 dark:text-white" />
      </div>
      <div>
        <label for="expense-category" class="mb-1 block text-xs text-gray-600 dark:text-gray-400">분류</label>
        <input id="expense-category" type="text" placeholder="예: 식대 / 교통비 / 출장비"
          class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm dark:border-gray-700 dark:bg-gray-900 dark:text-white" />
      </div>
      <div>
        <label for="expense-date" class="mb-1 block text-xs text-gray-600 dark:text-gray-400">지출일</label>
        <input id="expense-date" type="date"
          class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm dark:border-gray-700 dark:bg-gray-900 dark:text-white" />
      </div>
    </div>
    <div>
      <label for="expense-description" class="mb-1 block text-xs text-gray-600 dark:text-gray-400">상세 내역</label>
      <textarea id="expense-description" rows="3" placeholder="지출 상세 내역"
        class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm dark:border-gray-700 dark:bg-gray-900 dark:text-white"></textarea>
    </div>
  </div>
`;

/**
 * 스텝3 슬롯에 지출 필드 영역을 한 번 inject.
 * @param {Document|HTMLElement} root
 */
export function mountExpenseFormFields(root = document) {
  if (root.querySelector('#approval-expense-fields')) return;
  const slot = root.querySelector('#approval-dynamic-form-slot');
  if (!slot) return;
  slot.insertAdjacentHTML('beforeend', SECTION_HTML);

  // 지출일 row 전체 클릭 시 네이티브 date picker 가 열리도록.
  const dateInput = root.querySelector('#expense-date');
  if (dateInput instanceof HTMLInputElement) {
    attachShowPickerOnClick(dateInput);
  }
}

/**
 * 지출 입력값을 제출용 객체로 변환한다.
 * @param {Document|HTMLElement} root
 * @returns {{ amount:number, category:string, spentAt:string, description:string }}
 */
export function serializeExpenseForm(root = document) {
  return {
    amount: Number(getValue(root, '#expense-amount') || 0),
    category: getValue(root, '#expense-category'),
    spentAt: getValue(root, '#expense-date'),
    description: getValue(root, '#expense-description'),
  };
}

/**
 * 지출 입력값을 검증한다.
 * @param {Document|HTMLElement} root
 * @returns {{ valid: boolean, message: string, data: ReturnType<typeof serializeExpenseForm> }}
 */
export function validateExpenseForm(root = document) {
  const data = serializeExpenseForm(root);
  if (!Number.isFinite(data.amount) || data.amount <= 0) {
    return { valid: false, message: '금액을 입력하세요.', data };
  }
  if (!data.category) {
    return { valid: false, message: '지출 분류를 입력하세요.', data };
  }
  if (!data.spentAt) {
    return { valid: false, message: '지출일을 선택하세요.', data };
  }
  return { valid: true, message: '', data };
}

/**
 * 지출 입력 필드를 초기 상태로 되돌린다.
 * @param {Document|HTMLElement} root
 */
export function resetExpenseForm(root = document) {
  const amount = root.querySelector('#expense-amount');
  const category = root.querySelector('#expense-category');
  const date = root.querySelector('#expense-date');
  const desc = root.querySelector('#expense-description');
  if (amount instanceof HTMLInputElement) amount.value = '';
  if (category instanceof HTMLInputElement) category.value = '';
  if (date instanceof HTMLInputElement) date.value = '';
  if (desc instanceof HTMLTextAreaElement) desc.value = '';
}
