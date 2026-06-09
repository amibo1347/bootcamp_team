/**
 * 전자결재 첨부파일 — 기안 위저드 3단계 공통.
 * - 게시글 작성(board/create.js) 의 첨부 흐름과 동일:
 *   파일 선택 즉시 /api/approval-attachment 로 업로드 → 받은 id 를 제출 payload 에 동봉.
 * - 첨부는 선택 사항. 0개여도 제출 가능.
 */

const MAX_ATTACHMENTS = 5;
const MAX_ATTACHMENT_SIZE = 10 * 1024 * 1024; // 10MB

/** 사용자가 누적 선택한 원본 파일. @type {File[]} */
let selectedFiles = [];
/** 업로드 완료된 첨부 메타. @type {Array<{id:number, filename:string, size:number|null}>} */
let uploadedAttachments = [];
/** 업로드 진행 중 플래그 — 진행 중 제출 차단. */
let isUploading = false;

/**
 * CSRF 헤더 (POST multipart 용).
 * @returns {Record<string, string>}
 */
function csrfHeaders() {
  const token = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
  const headerName =
    document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || 'X-CSRF-TOKEN';
  return token ? { [headerName]: token } : {};
}

/**
 * 파일 고유 키 (중복 선택 방지용).
 * @param {File} file
 * @returns {string}
 */
function fileKey(file) {
  return `${file.name}__${file.size}__${file.lastModified}`;
}

/**
 * 업로드된 첨부 목록을 화면에 렌더링한다.
 */
function renderList() {
  const list = document.getElementById('approval-attachment-list');
  if (!list) return;

  if (!uploadedAttachments.length) {
    list.innerHTML = '<li class="text-gray-400">선택된 파일이 없습니다.</li>';
    return;
  }

  list.innerHTML = uploadedAttachments
    .map((att, index) => {
      const sizeText = Number.isFinite(Number(att.size))
        ? `${Math.ceil(Number(att.size) / 1024)} KB`
        : '-';
      const div = document.createElement('div');
      div.textContent = att.filename;
      const safeName = div.innerHTML;
      return `
        <li class="flex items-center justify-between gap-3">
          <span class="min-w-0 truncate">${safeName} (${sizeText})</span>
          <button type="button" data-approval-attachment-index="${index}"
            class="shrink-0 rounded-md bg-gray-100 px-2.5 py-1 text-xs font-medium text-gray-700 hover:bg-gray-200 dark:bg-white/10 dark:text-gray-200 dark:hover:bg-white/15">
            제거
          </button>
        </li>`;
    })
    .join('');
}

/**
 * 진행 상태 메시지를 목록 영역에 표시한다.
 * @param {string} message
 */
function renderState(message) {
  const list = document.getElementById('approval-attachment-list');
  if (list) list.innerHTML = `<li class="text-gray-400">${message}</li>`;
}

/**
 * 첨부파일 1개를 업로드한다.
 * @param {File} file
 * @returns {Promise<{id:number, filename:string, size:number|null}>}
 */
async function uploadOne(file) {
  const formData = new FormData();
  formData.append('file', file);

  const response = await fetch('/api/approval-attachment', {
    method: 'POST',
    headers: csrfHeaders(), // multipart: Content-Type 은 브라우저가 자동 설정
    body: formData,
    credentials: 'same-origin',
  });

  if (!response.ok) {
    const fallback =
      response.status === 413
        ? '첨부파일 용량이 너무 큽니다.'
        : '첨부파일 업로드에 실패했습니다.';
    throw new Error(await window.getApiErrorMessage(response, fallback));
  }

  const data = await response.json();
  if (!data?.id) throw new Error('첨부파일 ID를 받지 못했습니다.');
  return {
    id: Number(data.id),
    filename: data.filename || file.name,
    size: data.size ?? file.size,
  };
}

/**
 * 파일 input change 처리 — 용량/개수 검증 후 업로드까지 수행.
 * @param {Event} event
 */
async function handleChange(event) {
  const input = event.currentTarget;
  const files = Array.from(input?.files || []);
  if (!files.length) return;

  if (files.some((file) => file.size > MAX_ATTACHMENT_SIZE)) {
    alert('파일당 최대 10MB까지 업로드할 수 있습니다.');
    input.value = '';
    return;
  }

  const existingKeys = new Set(selectedFiles.map(fileKey));
  const newFiles = files.filter((file) => !existingKeys.has(fileKey(file)));
  const merged = [...selectedFiles, ...newFiles];

  if (merged.length > MAX_ATTACHMENTS) {
    alert(`첨부파일은 최대 ${MAX_ATTACHMENTS}개까지 첨부할 수 있습니다.`);
    input.value = '';
    return;
  }

  try {
    isUploading = true;
    input.disabled = true;
    renderState('첨부파일 업로드 중...');

    const results = [];
    for (const file of newFiles) {
      // eslint-disable-next-line no-await-in-loop
      results.push(await uploadOne(file));
    }

    selectedFiles = merged;
    uploadedAttachments = [...uploadedAttachments, ...results];
    renderList();
  } catch (error) {
    alert(error?.message || '첨부파일 업로드 중 오류가 발생했습니다.');
    renderList();
  } finally {
    isUploading = false;
    input.disabled = false;
    input.value = '';
  }
}

/**
 * 제거 버튼 클릭 — 업로드 목록에서 제외.
 * @param {MouseEvent} event
 */
function handleRemove(event) {
  const button = event.target?.closest?.('button[data-approval-attachment-index]');
  if (!button) return;
  const index = Number(button.dataset.approvalAttachmentIndex);
  if (!Number.isFinite(index)) return;

  const removed = uploadedAttachments[index];
  uploadedAttachments = uploadedAttachments.filter((_, i) => i !== index);
  if (removed) {
    selectedFiles = selectedFiles.filter(
      (file) =>
        !(file.name === removed.filename && Number(file.size || 0) === Number(removed.size || 0))
    );
  }
  renderList();
}

/**
 * 첨부 input/목록에 이벤트 리스너를 1회 부착한다.
 * @param {Document|HTMLElement} root
 */
export function mountApprovalAttachment(root = document) {
  const input = root.querySelector('#approval-attachments');
  if (input && !input.dataset.approvalAttachmentBound) {
    input.dataset.approvalAttachmentBound = 'true';
    input.addEventListener('change', handleChange);
  }
  const list = root.querySelector('#approval-attachment-list');
  if (list && !list.dataset.approvalAttachmentBound) {
    list.dataset.approvalAttachmentBound = 'true';
    list.addEventListener('click', handleRemove);
  }
}

/**
 * 제출 payload 에 동봉할 첨부 id 목록.
 * @returns {number[]}
 */
export function getApprovalAttachmentIds() {
  return uploadedAttachments.map((att) => att.id);
}

/**
 * 업로드가 아직 진행 중인지 여부.
 * @returns {boolean}
 */
export function isApprovalAttachmentUploading() {
  return isUploading;
}

/**
 * 첨부 상태를 초기화한다 (제출 완료 후 위저드 리셋 시 호출).
 */
export function resetApprovalAttachments() {
  selectedFiles = [];
  uploadedAttachments = [];
  isUploading = false;
  const input = document.getElementById('approval-attachments');
  if (input instanceof HTMLInputElement) input.value = '';
  renderList();
}

// 모듈 로드 시 즉시 mount 시도. DOM 이 아직이면 DOMContentLoaded 에서 재시도.
if (typeof document !== 'undefined') {
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => mountApprovalAttachment(document));
  } else {
    mountApprovalAttachment(document);
  }
}
