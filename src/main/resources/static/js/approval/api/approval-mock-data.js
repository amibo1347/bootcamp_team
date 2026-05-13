/**
 * 전자결재 Mock 데이터 및 메모리 저장소
 * - 서버 enum은 PENDING/APPROVED/REJECTED 만 존재 → ON_HOLD는 Mock 전용
 */

import { MOCK_DELAY_MS, DEFAULT_PAGE_SIZE } from './approval-config.js';

/** @typedef {'PENDING'|'APPROVED'|'REJECTED'} ServerApprovalStatus */
/** @typedef {'PENDING'|'APPROVED'|'REJECTED'|'ON_HOLD'} MockApprovalStatus */

/**
 * 지연 후 값 반환 (비동기 UX 연습)
 * @template T
 * @param {T} value
 * @returns {Promise<T>}
 */
function delay(value) {
  return new Promise((resolve) => {
    setTimeout(() => resolve(value), MOCK_DELAY_MS);
  });
}

/** 양식 템플릿 목록 (고정) */
export const MOCK_FORM_TEMPLATES = [
  { id: 1, formCode: 'VACATION', name: '휴가 신청', isActive: true },
  { id: 2, formCode: 'GENERIC', name: '일반 기안', isActive: true },
];

/** 결재자 후보 (고정) */
export const MOCK_APPROVER_CANDIDATES = [
  { memberId: 501, name: '김결재', deptName: '경영지원' },
  { memberId: 502, name: '이승인', deptName: '인사' },
  { memberId: 503, name: '박검토', deptName: '총무' },
];

/** @type {MockApprovalRow[]} */
let approvalRows = [];

/** @typedef {Object} MockApprovalRow
 * @property {number} approvalId
 * @property {string} title
 * @property {MockApprovalStatus} status
 * @property {number} formTemplateId
 * @property {string} formCode
 * @property {number} drafterMemberId
 * @property {string} drafterName
 * @property {number} approverMemberId
 * @property {string} approverName
 * @property {string|null} approverComment
 * @property {string} draftedAt ISO
 * @property {string|null} processedAt ISO
 * @property {Record<string, unknown>} [payload] 제출 본문(Mock)
 */

function seedApprovals() {
  const now = new Date().toISOString();
  approvalRows = [
    {
      approvalId: 88001,
      title: '[휴가] 연차 1일',
      status: 'PENDING',
      formTemplateId: 1,
      formCode: 'VACATION',
      drafterMemberId: 101,
      drafterName: '홍기안',
      approverMemberId: 501,
      approverName: '김결재',
      approverComment: null,
      draftedAt: now,
      processedAt: null,
      payload: { vacationType: '연차', startDate: '2026-05-20', endDate: '2026-05-20' },
    },
    {
      approvalId: 88002,
      title: '[휴가] 반차',
      status: 'APPROVED',
      formTemplateId: 1,
      formCode: 'VACATION',
      drafterMemberId: 102,
      drafterName: '신직원',
      approverMemberId: 502,
      approverName: '이승인',
      approverComment: '승인합니다.',
      draftedAt: new Date(Date.now() - 86400000).toISOString(),
      processedAt: new Date(Date.now() - 3600000).toISOString(),
      payload: {},
    },
    {
      approvalId: 88003,
      title: '[일반] 장비 구매 요청',
      status: 'REJECTED',
      formTemplateId: 2,
      formCode: 'GENERIC',
      drafterMemberId: 103,
      drafterName: '최요청',
      approverMemberId: 501,
      approverName: '김결재',
      approverComment: '예산 초과로 반려합니다.',
      draftedAt: new Date(Date.now() - 172800000).toISOString(),
      processedAt: new Date(Date.now() - 86400000).toISOString(),
      payload: {},
    },
    {
      approvalId: 88004,
      title: '[일반] 예산 검토(보류 데모)',
      status: 'ON_HOLD',
      formTemplateId: 2,
      formCode: 'GENERIC',
      drafterMemberId: 104,
      drafterName: '한검토',
      approverMemberId: 501,
      approverName: '김결재',
      approverComment: '추가 자료 대기(M)',
      draftedAt: new Date(Date.now() - 259200000).toISOString(),
      processedAt: new Date(Date.now() - 43200000).toISOString(),
      payload: {},
    },
  ];
}

seedApprovals();

let nextApprovalId = 89000;

/**
 * Mock: 양식 목록
 * @returns {Promise<typeof MOCK_FORM_TEMPLATES>}
 */
export async function mockGetFormTemplates() {
  return delay([...MOCK_FORM_TEMPLATES]);
}

/**
 * Mock: 결재자 후보
 * @returns {Promise<typeof MOCK_APPROVER_CANDIDATES>}
 */
export async function mockGetApproverCandidates() {
  return delay([...MOCK_APPROVER_CANDIDATES]);
}

/**
 * Mock: 내 결재함
 * @param {{ status?: string|null, page?: number, memberId?: number|null }} opts
 */
export async function mockListMyApprovals(opts = {}) {
  const page = Math.max(1, opts.page ?? 1);
  const memberId = opts.memberId;
  let rows = approvalRows.filter(
    (r) => r.status === 'PENDING' || r.status === 'APPROVED' || r.status === 'REJECTED' || r.status === 'ON_HOLD',
  );
  if (memberId != null) {
    rows = rows.filter((r) => r.drafterMemberId === memberId);
  }
  if (opts.status && opts.status !== 'ALL') {
    rows = rows.filter((r) => r.status === opts.status);
  }
  const total = rows.length;
  const start = (page - 1) * DEFAULT_PAGE_SIZE;
  const slice = rows.slice(start, start + DEFAULT_PAGE_SIZE);
  return delay({
    items: slice,
    page,
    pageSize: DEFAULT_PAGE_SIZE,
    total,
    totalPages: Math.max(1, Math.ceil(total / DEFAULT_PAGE_SIZE)),
  });
}

/**
 * Mock: 관리자 대기함 (PENDING · 본인이 결재자인 건만 시뮬)
 * @param {{ approverMemberId?: number|null }} opts
 */
export async function mockListPendingForAdmin(opts = {}) {
  const aid = opts.approverMemberId;
  let rows = approvalRows.filter((r) => r.status === 'PENDING');
  if (aid != null) {
    rows = rows.filter((r) => r.approverMemberId === aid);
  }
  return delay({ items: [...rows], total: rows.length });
}

/**
 * Mock: 관리자 완료함
 * @param {{ approverMemberId?: number|null }} opts
 */
export async function mockListCompletedForAdmin(opts = {}) {
  const aid = opts.approverMemberId;
  let rows = approvalRows.filter((r) => r.status === 'APPROVED' || r.status === 'REJECTED' || r.status === 'ON_HOLD');
  if (aid != null) {
    rows = rows.filter((r) => r.approverMemberId === aid);
  }
  return delay({ items: [...rows], total: rows.length });
}

/**
 * Mock: 기안 제출
 * @param {Record<string, unknown>} payload
 */
export async function mockSubmitApproval(payload) {
  const formTemplateId = Number(payload.formTemplateId);
  const tmpl = MOCK_FORM_TEMPLATES.find((t) => t.id === formTemplateId) ?? MOCK_FORM_TEMPLATES[0];
  const approverMemberId = Number(payload.approverMemberId);
  const approver = MOCK_APPROVER_CANDIDATES.find((c) => c.memberId === approverMemberId) ?? MOCK_APPROVER_CANDIDATES[0];
  const drafterMemberId = payload.drafterMemberId != null ? Number(payload.drafterMemberId) : 101;
  const drafterName = typeof payload.drafterName === 'string' ? payload.drafterName : '나기안';
  const title = typeof payload.title === 'string' ? payload.title : '[기안] 제목 없음';

  const row = {
    approvalId: nextApprovalId++,
    title,
    status: /** @type {MockApprovalStatus} */ ('PENDING'),
    formTemplateId: tmpl.id,
    formCode: tmpl.formCode,
    drafterMemberId,
    drafterName,
    approverMemberId: approver.memberId,
    approverName: approver.name,
    approverComment: null,
    draftedAt: new Date().toISOString(),
    processedAt: null,
    payload: payload.vacation && typeof payload.vacation === 'object' ? payload.vacation : {},
  };
  approvalRows.unshift(row);
  return delay({ ok: true, approvalId: row.approvalId, message: 'Mock 제출 완료' });
}

/**
 * Mock: 결재 처리 (APPROVE | REJECT | HOLD)
 * HOLD → Mock 상태 ON_HOLD (서버 enum 없음)
 * @param {{ approvalId: number, action: string, comment?: string }} body
 */
export async function mockProcessApproval(body) {
  const id = Number(body.approvalId);
  const row = approvalRows.find((r) => r.approvalId === id);
  if (!row) {
    return delay({ ok: false, message: '문서를 찾을 수 없습니다(M).' });
  }
  const action = String(body.action || '').toUpperCase();
  const comment = typeof body.comment === 'string' ? body.comment : '';
  const now = new Date().toISOString();

  if (action === 'APPROVE') {
    row.status = 'APPROVED';
    row.approverComment = comment || '승인';
    row.processedAt = now;
  } else if (action === 'REJECT') {
    row.status = 'REJECTED';
    row.approverComment = comment || '반려';
    row.processedAt = now;
  } else if (action === 'HOLD') {
    row.status = 'ON_HOLD';
    row.approverComment = comment || '보류(M)';
    row.processedAt = now;
  } else {
    return delay({ ok: false, message: '알 수 없는 action' });
  }
  return delay({ ok: true, approvalId: id, status: row.status });
}

/** 테스트용: Mock 저장소 초기화 */
export function resetMockApprovalsForTests() {
  seedApprovals();
  nextApprovalId = 89000;
}
