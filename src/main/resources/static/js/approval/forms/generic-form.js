/**
 * 일반 기안(GENERIC) 양식 유틸
 * - 본문 textarea 1개만 사용. serialize/validate/reset 트리오.
 * - 필드 HTML 은 mountGenericFormFields() 가 한 번 inject (form-registry 가 호출).
 */

const SECTION_HTML = `
  <div id="approval-generic-fields" data-approval-form-section="GENERIC"
       class="hidden space-y-3 rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-white/[0.03]">
    <p class="text-xs font-semibold text-gray-700 dark:text-gray-300">기안 본문</p>
    <div>
      <label for="generic-content" class="mb-1 block text-xs text-gray-600 dark:text-gray-400">내용</label>
      <textarea id="generic-content" rows="6" placeholder="결재 사유와 세부 내용을 입력하세요."
        class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm dark:border-gray-700 dark:bg-gray-900 dark:text-white"></textarea>
    </div>
  </div>
`;

/**
 * 스텝3 슬롯에 일반 기안 필드 영역을 한 번 inject.
 * @param {Document|HTMLElement} root
 */
export function mountGenericFormFields(root = document) {
  if (root.querySelector('#approval-generic-fields')) return;
  const slot = root.querySelector('#approval-dynamic-form-slot');
  if (!slot) return;
  slot.insertAdjacentHTML('beforeend', SECTION_HTML);
}

/**
 * 문자열 입력값 안전 추출.
 * @param {Document|HTMLElement} root
 * @param {string} selector
 * @returns {string}
 */
function getValue(root, selector) {
  const el = root.querySelector(selector);
  if (!(el instanceof HTMLInputElement || el instanceof HTMLTextAreaElement || el instanceof HTMLSelectElement)) {
    return '';
  }
  return el.value.trim();
}

/**
 * 일반 기안 입력값을 제출용 객체로 변환한다.
 * @param {Document|HTMLElement} root
 * @returns {{ content: string }}
 */
export function serializeGenericForm(root = document) {
  return {
    content: getValue(root, '#generic-content'),
  };
}

/**
 * 일반 기안 입력값을 검증한다.
 * @param {Document|HTMLElement} root
 * @returns {{ valid: boolean, message: string, data: ReturnType<typeof serializeGenericForm> }}
 */
export function validateGenericForm(root = document) {
  const data = serializeGenericForm(root);
  if (!data.content) {
    return { valid: false, message: '본문을 입력하세요.', data };
  }
  return { valid: true, message: '', data };
}

/**
 * 일반 기안 입력 필드를 초기 상태로 되돌린다.
 * @param {Document|HTMLElement} root
 */
export function resetGenericForm(root = document) {
  const content = root.querySelector('#generic-content');
  if (content instanceof HTMLTextAreaElement) content.value = '';
}
