/**
 * 전자결재 페이지 초기화
 * - 탭 전환과 공통 데이터 로드를 담당한다.
 * - 위저드와 내 결재함은 각각 user/wizard-controller.js, user/my-inbox.js에서 처리한다.
 */

import * as approvalClient from './api/approval-client.js';
import { renderApprovalStatusBadge } from './common/status-badges.js';
import {
  renderApproverCell,
  bindApproverDropdownToggle,
} from './common/approver-cell.js';
import { initMyInbox } from './user/my-inbox.js';
import { initApprovalWizard } from './user/wizard-controller.js';
import { populateVacationTypeOptions } from './forms/vacation-form.js';
import { openApprovalDetailModal } from './user/approval-detail-modal.js';

/** @type {{ refresh: () => Promise<void> }|null} */
let myInboxController = null;

/**
 * body data-member-id에서 로그인 사용자 식별자를 읽는다.
 * @returns {number|null}
 */
function getMemberIdFromDom() {
  const raw = document.body?.dataset?.memberId;
  if (raw === undefined || raw === '') return null;
  const n = Number(raw);
  return Number.isFinite(n) ? n : null;
}

/**
 * HTML 이스케이프
 * @param {unknown} value
 * @returns {string}
 */
function escapeHtml(value) {
  const div = document.createElement('div');
  div.textContent = value == null ? '' : String(value);
  return div.innerHTML;
}

/**
 * ISO 날짜 문자열을 목록 표시 형식으로 변환한다.
 * @param {unknown} value
 * @returns {string}
 */
function formatDateTime(value) {
  return String(value || '').slice(0, 16).replace('T', ' ');
}

/**
 * 관리자 대기함 목록을 갱신한다.
 * @returns {Promise<void>}
 */
async function refreshPending() {
  const tbody = document.getElementById('approval-pending-body');
  const emptyWrap = document.getElementById('approval-pending-empty');
  if (!tbody) return;

  const memberId = getMemberIdFromDom();
  const response = await approvalClient.listPendingForAdmin({ approverMemberId: memberId ?? undefined });
  const items = Array.isArray(response.items) ? response.items : [];

  tbody.innerHTML = '';
  items.forEach((row) => {
    const tr = document.createElement('tr');
    tr.dataset.approvalId = String(row.approvalId);
    tr.innerHTML = `
      <td class="whitespace-nowrap px-4 py-3 text-sm text-gray-800 dark:text-gray-200">${escapeHtml(row.approvalId)}</td>
      <td class="px-4 py-3 text-xs text-gray-500 dark:text-gray-400">${escapeHtml(row.formCode || '')}</td>
      <td class="px-4 py-3 text-sm text-gray-900 dark:text-white">
        <button type="button" class="approval-detail-trigger text-left underline-offset-2 hover:underline">${escapeHtml(row.title)}</button>
      </td>
      <td class="px-4 py-3 text-sm text-gray-600 dark:text-gray-300">${escapeHtml(row.drafterName)}</td>
      <td class="px-4 py-3">${renderApprovalStatusBadge(String(row.status || ''))}</td>
      ${renderApproverCell(row)}
      <td class="whitespace-nowrap px-4 py-3 text-xs text-gray-500 dark:text-gray-400">${escapeHtml(formatDateTime(row.draftedAt))}</td>
      <td class="px-4 py-3">
        <div class="flex flex-wrap gap-1">
          <button type="button" data-act="APPROVE" class="approval-act rounded bg-emerald-600 px-2 py-1 text-xs text-white hover:bg-emerald-700">승인</button>
          <button type="button" data-act="REJECT" class="approval-act rounded bg-red-600 px-2 py-1 text-xs text-white hover:bg-red-700">반려</button>
          <button type="button" data-act="HOLD" class="approval-act rounded bg-violet-600 px-2 py-1 text-xs text-white hover:bg-violet-700">보류</button>
        </div>
      </td>`;
    tbody.appendChild(tr);
  });

  emptyWrap?.classList.toggle('hidden', items.length > 0);
}

/** @type {Array<Record<string, unknown>>} */
let lastCompletedItems = [];

/**
 * 관리자 완료함 목록을 갱신한다.
 * @returns {Promise<void>}
 */
async function refreshCompleted() {
  const memberId = getMemberIdFromDom();
  const response = await approvalClient.listCompletedForAdmin({ approverMemberId: memberId ?? undefined });
  lastCompletedItems = Array.isArray(response.items) ? response.items : [];
  const filter = document.getElementById('approval-completed-filter');
  renderCompletedRows(filter instanceof HTMLSelectElement ? filter.value : 'ALL');
}

/**
 * 관리자 완료함 행을 현재 필터 기준으로 렌더링한다.
 * @param {string} filterValue
 */
function renderCompletedRows(filterValue) {
  const tbody = document.getElementById('approval-completed-body');
  const emptyWrap = document.getElementById('approval-completed-empty');
  if (!tbody) return;

  const rows = filterValue && filterValue !== 'ALL'
    ? lastCompletedItems.filter((row) => row.status === filterValue)
    : lastCompletedItems;

  tbody.innerHTML = '';
  rows.forEach((row) => {
    const tr = document.createElement('tr');
    tr.dataset.approvalId = String(row.approvalId);
    const commentRaw = typeof row.approverComment === 'string' ? row.approverComment.trim() : '';
    const commentTrunc = commentRaw.length > 18 ? `${commentRaw.slice(0, 18)}…` : commentRaw;
    const commentCell = commentRaw
      ? `<td class="px-4 py-3 text-xs text-gray-600 dark:text-gray-300" title="${escapeHtml(commentRaw)}">${escapeHtml(commentTrunc)}</td>`
      : `<td class="px-4 py-3 text-xs text-gray-400 dark:text-gray-500">—</td>`;

    // 신청자 셀 — 사진 + 이름만 (부서/직급 없음)
    const drafterId = row.drafterMemberId;
    const drafterImg = drafterId != null
      ? `<img src="/api/member/${encodeURIComponent(String(drafterId))}/profileImg" alt="" class="h-8 w-8 rounded-full object-cover ring-1 ring-gray-200 dark:ring-gray-700" />`
      : '';
    const drafterCell = `
      <td class="px-4 py-3">
        <div class="flex items-center gap-2">
          ${drafterImg}
          <span class="truncate text-sm text-gray-800 dark:text-gray-200">${escapeHtml(row.drafterName || '')}</span>
        </div>
      </td>`;

    tr.innerHTML = `
      <td class="whitespace-nowrap px-4 py-3 text-sm text-gray-800 dark:text-gray-200">${escapeHtml(row.approvalId)}</td>
      <td class="px-4 py-3 text-sm text-gray-900 dark:text-white">
        <button type="button" class="approval-detail-trigger text-left underline-offset-2 hover:underline">${escapeHtml(row.title)}</button>
      </td>
      ${drafterCell}
      <td class="px-4 py-3">${renderApprovalStatusBadge(String(row.status || ''))}</td>
      ${renderApproverCell(row)}
      ${commentCell}
      <td class="whitespace-nowrap px-4 py-3 text-xs text-gray-500 dark:text-gray-400">${escapeHtml(formatDateTime(row.processedAt))}</td>`;
    tbody.appendChild(tr);
  });

  emptyWrap?.classList.toggle('hidden', rows.length > 0);
}

/**
 * 탭 활성 스타일과 패널 표시를 전환한다.
 * @param {'wizard'|'my'|'pending'|'completed'} id
 */
function activateTab(id) {
  document.querySelectorAll('[data-approval-tab]').forEach((btn) => {
    const active = btn.getAttribute('data-approval-tab') === id;
    btn.classList.toggle('bg-brand-500', active);
    btn.classList.toggle('text-white', active);
    btn.classList.toggle('font-medium', active);
    btn.classList.toggle('text-gray-600', !active);
    btn.classList.toggle('dark:text-gray-300', !active);
    btn.classList.toggle('hover:bg-gray-100', !active);
    btn.classList.toggle('dark:hover:bg-white/10', !active);
  });

  document.querySelectorAll('.approval-panel').forEach((panel) => {
    const panelId = panel.id.replace('approval-panel-', '');
    panel.classList.toggle('hidden', panelId !== id);
  });
}

/**
 * 탭, 필터, 관리자 처리 버튼 이벤트를 연결한다.
 */
function bindEvents() {
  document.querySelectorAll('[data-approval-tab]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const id = btn.getAttribute('data-approval-tab');
      if (!id) return;
      activateTab(/** @type {'wizard'|'my'|'pending'|'completed'} */ (id));
      if (id === 'my') void myInboxController?.refresh();
      if (id === 'pending') void refreshPending();
      if (id === 'completed') void refreshCompleted();
    });
  });

  document.getElementById('approval-completed-filter')?.addEventListener('change', (event) => {
    const value = event.target instanceof HTMLSelectElement ? event.target.value : 'ALL';
    renderCompletedRows(value);
  });

  // 완료함 결재자 셀 클릭 → 단계별 드롭다운(오버레이) 토글
  const completedBody = document.getElementById('approval-completed-body');
  if (completedBody instanceof HTMLElement) {
    bindApproverDropdownToggle(completedBody);
  }

  // 대기함도 결재선 셀 드롭다운 토글 활성화 (새 컬럼)
  const pendingBody = document.getElementById('approval-pending-body');
  if (pendingBody instanceof HTMLElement) {
    bindApproverDropdownToggle(pendingBody);
  }

  // 완료함 행 제목 클릭 → 상세 모달
  document.getElementById('approval-completed-body')?.addEventListener('click', (event) => {
    const target = event.target;
    if (!(target instanceof HTMLElement)) return;
    const trigger = target.closest('.approval-detail-trigger');
    if (!(trigger instanceof HTMLElement)) return;
    const row = trigger.closest('tr');
    const id = Number(row?.dataset.approvalId);
    if (Number.isFinite(id)) openApprovalDetailModal(id);
  });

  document.getElementById('approval-pending-body')?.addEventListener('click', async (event) => {
    const target = event.target;
    if (!(target instanceof HTMLElement)) return;

    // 제목 클릭 → 상세 모달 (액션 버튼보다 먼저 처리)
    const detailTrigger = target.closest('.approval-detail-trigger');
    if (detailTrigger instanceof HTMLElement) {
      const row = detailTrigger.closest('tr');
      const id = Number(row?.dataset.approvalId);
      if (Number.isFinite(id)) openApprovalDetailModal(id);
      return;
    }

    const actionButton = target.closest('.approval-act');
    if (!(actionButton instanceof HTMLElement)) return;

    const row = actionButton.closest('tr');
    const approvalId = Number(row?.dataset.approvalId);
    const action = actionButton.getAttribute('data-act');
    if (!Number.isFinite(approvalId) || !action) return;

    let comment = '';
    if (action === 'REJECT' || action === 'HOLD') {
      const reason = window.prompt(action === 'HOLD' ? '보류 사유' : '반려 사유', '');
      if (reason === null) return;
      comment = reason.trim();
      if (!comment) {
        window.alert(action === 'HOLD' ? '보류 사유를 입력하세요.' : '반려 사유를 입력하세요.');
        return;
      }
    }

    await approvalClient.processApproval({ approvalId, action, comment });
    await refreshPending();
    await refreshCompleted();
    await myInboxController?.refresh();
  });
}

/**
 * 최초 진입 시 Mock/API 데이터를 로드하고 하위 컨트롤러를 시작한다.
 * @returns {Promise<void>}
 */
async function initializeApprovalPage() {
  const memberId = getMemberIdFromDom();

  try {
    const [templates, candidates, vacationTypes] = await Promise.all([
      approvalClient.getFormTemplates(),
      approvalClient.getApproverCandidates(),
      approvalClient.getVacationTypes(),
    ]);

    populateVacationTypeOptions(document, Array.isArray(vacationTypes) ? vacationTypes : []);

    myInboxController = initMyInbox({ memberId });
    initApprovalWizard({
      templates: Array.isArray(templates) ? templates : [],
      candidates: Array.isArray(candidates) ? candidates : [],
      memberId,
      onSubmitted: async () => {
        await myInboxController?.refresh();
        await refreshPending();
        await refreshCompleted();
      },
    });

    // URL hash 기반 초기 탭 진입 (예: /approval#pending — 결재 신청 알림 클릭 경로)
    const hash = (window.location.hash || '').replace('#', '');
    if (hash && ['wizard', 'my', 'pending', 'completed'].includes(hash)) {
      activateTab(/** @type {'wizard'|'my'|'pending'|'completed'} */ (hash));
      if (hash === 'my') void myInboxController?.refresh();
      if (hash === 'pending') void refreshPending();
      if (hash === 'completed') void refreshCompleted();
    }
  } catch (error) {
    console.error('[approval-main] 초기 로드 실패', error);
  }
}

document.addEventListener('DOMContentLoaded', () => {
  bindEvents();
  void initializeApprovalPage();
});
