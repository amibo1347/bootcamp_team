/**
 * 전자결재 API 단일 진입점
 * - USE_APPROVAL_MOCK === true → Mock만 사용
 * - false → 실 GET/POST 시도 후 실패 시 Mock 폴백 (tryRealThenMock)
 *
 * HTTP: GET·POST만 사용 (프로젝트 규칙)
 */

import { USE_APPROVAL_MOCK, APPROVAL_API_BASE } from './approval-config.js';
import {
  mockGetFormTemplates,
  mockGetApproverCandidates,
  mockSubmitApproval,
  mockListMyApprovals,
  mockListPendingForAdmin,
  mockListCompletedForAdmin,
  mockProcessApproval,
  mockGetVacationTypes,
  mockGetApprovalDetail,
} from './approval-mock-data.js';

/**
 * 문서 메타에서 CSRF 헤더 구성 (POST용)
 * @returns {Record<string, string>}
 */
function csrfHeaders() {
  const token = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
  const headerName = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || 'X-CSRF-TOKEN';
  return token ? { [headerName]: token } : {};
}

/**
 * 실패 시 예외를 던지는 fetch JSON (GET)
 * @param {string} url
 */
async function fetchJsonGet(url) {
  const res = await fetch(url, {
    method: 'GET',
    credentials: 'same-origin',
    headers: { Accept: 'application/json' },
  });
  if (!res.ok) {
    throw new Error(await window.getApiErrorMessage(res, `GET ${url} → ${res.status}`));
  }
  return res.json();
}

/**
 * 실패 시 예외를 던지는 fetch JSON (POST)
 * @param {string} url
 * @param {unknown} body
 */
async function fetchJsonPost(url, body) {
  const res = await fetch(url, {
    method: 'POST',
    credentials: 'same-origin',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      ...csrfHeaders(),
    },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    throw new Error(await window.getApiErrorMessage(res, `POST ${url} → ${res.status}`));
  }
  return res.json();
}

/**
 * 실 API를 시도하고, 네트워크 오류·HTTP 오류 시 Mock 함수로 폴백
 * @template T
 * @param {() => Promise<T>} realFn
 * @param {() => Promise<T>} mockFn
 * @returns {Promise<T>}
 */
export async function tryRealThenMock(realFn, mockFn) {
  if (USE_APPROVAL_MOCK) {
    return mockFn();
  }
  try {
    return await realFn();
  } catch (err) {
    console.warn('[approval-client] 실 API 실패 — Mock 폴백', err);
    return mockFn();
  }
}

// --- 계약용 공개 메서드 (향후 URL·body만 실제 서버에 맞게 조정) ---

export function getFormTemplates() {
  return tryRealThenMock(
    () => fetchJsonGet(`${APPROVAL_API_BASE}/form-templates`),
    () => mockGetFormTemplates(),
  );
}

export function getApproverCandidates() {
  return tryRealThenMock(
    () => fetchJsonGet(`${APPROVAL_API_BASE}/approver-candidates`),
    () => mockGetApproverCandidates(),
  );
}

/**
 * 휴가 유형(VacationType enum) 옵션 조회.
 * @returns {Promise<Array<{ name: string, description: string }>>}
 */
export function getVacationTypes() {
  return tryRealThenMock(
    () => fetchJsonGet(`${APPROVAL_API_BASE}/vacation-types`),
    () => mockGetVacationTypes(),
  );
}

/**
 * @param {Record<string, unknown>} payload
 */
export function submitApproval(payload) {
  return tryRealThenMock(
    () => fetchJsonPost(`${APPROVAL_API_BASE}/submit`, payload),
    () => mockSubmitApproval(payload),
  );
}

/**
 * @param {{ status?: string|null, page?: number, memberId?: number|null }} opts
 */
export function listMyApprovals(opts = {}) {
  const q = new URLSearchParams();
  if (opts.status) q.set('status', String(opts.status));
  if (opts.page != null) q.set('page', String(opts.page));
  if (opts.memberId != null) q.set('memberId', String(opts.memberId));
  const qs = q.toString();
  const url = qs ? `${APPROVAL_API_BASE}/my?${qs}` : `${APPROVAL_API_BASE}/my`;
  return tryRealThenMock(
    () => fetchJsonGet(url),
    () => mockListMyApprovals(opts),
  );
}

/**
 * @param {{ approverMemberId?: number|null }} opts
 */
export function listPendingForAdmin(opts = {}) {
  const q = new URLSearchParams();
  if (opts.approverMemberId != null) q.set('approverMemberId', String(opts.approverMemberId));
  const qs = q.toString();
  const url = qs ? `${APPROVAL_API_BASE}/admin/pending?${qs}` : `${APPROVAL_API_BASE}/admin/pending`;
  return tryRealThenMock(
    () => fetchJsonGet(url),
    () => mockListPendingForAdmin(opts),
  );
}

/**
 * @param {{ approverMemberId?: number|null }} opts
 */
export function listCompletedForAdmin(opts = {}) {
  const q = new URLSearchParams();
  if (opts.approverMemberId != null) q.set('approverMemberId', String(opts.approverMemberId));
  const qs = q.toString();
  const url = qs ? `${APPROVAL_API_BASE}/admin/completed?${qs}` : `${APPROVAL_API_BASE}/admin/completed`;
  return tryRealThenMock(
    () => fetchJsonGet(url),
    () => mockListCompletedForAdmin(opts),
  );
}

/**
 * @param {{ approvalId: number, action: string, comment?: string }} body
 */
export function processApproval(body) {
  return tryRealThenMock(
    () => fetchJsonPost(`${APPROVAL_API_BASE}/process`, body),
    () => mockProcessApproval(body),
  );
}

/**
 * 결재 단건 상세 (헤더 + 결재선 + 양식별 본문).
 * @param {number} approvalId
 */
export function getApprovalDetail(approvalId) {
  return tryRealThenMock(
    () => fetchJsonGet(`${APPROVAL_API_BASE}/${encodeURIComponent(String(approvalId))}`),
    () => mockGetApprovalDetail(approvalId),
  );
}

/**
 * 결재 문서 삭제/취소 (기안자 본인). Mock 폴백 없이 실 API 만 호출.
 * @param {number} approvalId
 */
export function deleteApproval(approvalId) {
  return fetchJsonPost(`${APPROVAL_API_BASE}/${encodeURIComponent(String(approvalId))}/delete`, {});
}

// ===== Admin: 양식 관리 =====
// admin 엔드포인트는 Mock 폴백 없이 실 API 만 호출 (관리자 페이지에서만 사용).

const ADMIN_TEMPLATE_BASE = `${APPROVAL_API_BASE}/admin/form-templates`;

/** 관리자용 전체 양식 목록 (회사 양식 + fork 안 한 시스템 디폴트, 비활성 포함). */
export function listAdminFormTemplates() {
  return fetchJsonGet(ADMIN_TEMPLATE_BASE);
}

/**
 * 관리자용 단건 조회.
 * @param {number|string} id
 */
export function getAdminFormTemplate(id) {
  return fetchJsonGet(`${ADMIN_TEMPLATE_BASE}/${encodeURIComponent(String(id))}`);
}

/**
 * 시스템 디폴트 → 회사 사본 복사 (커스터마이즈 진입).
 * @param {string} formCode
 */
export function forkFormTemplate(formCode) {
  return fetchJsonPost(`${ADMIN_TEMPLATE_BASE}/fork`, { formCode });
}

/**
 * 새 양식 생성.
 * @param {{ formCode: string, name: string, content?: string, fieldSchema?: string|null }} dto
 */
export function createFormTemplate(dto) {
  return fetchJsonPost(ADMIN_TEMPLATE_BASE, dto);
}

/**
 * 양식 수정 (회사 사본만 가능).
 * @param {number|string} id
 * @param {{ name?: string, content?: string, active?: boolean, fieldSchema?: string|null }} dto
 */
export function updateFormTemplate(id, dto) {
  return fetchJsonPost(`${ADMIN_TEMPLATE_BASE}/${encodeURIComponent(String(id))}`, dto);
}

/**
 * 양식 삭제 (회사 사본만 가능 — 시스템 디폴트는 다시 노출됨).
 * @param {number|string} id
 */
export function deleteFormTemplate(id) {
  return fetchJsonPost(`${ADMIN_TEMPLATE_BASE}/${encodeURIComponent(String(id))}/delete`, {});
}
