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
    throw new Error(`GET ${url} → ${res.status}`);
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
    throw new Error(`POST ${url} → ${res.status}`);
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
