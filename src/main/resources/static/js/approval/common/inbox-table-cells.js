/**
 * 전자결재 함 목록 테이블 — 공통 셀 렌더 (내 결재함 스타일 기준)
 */

import { renderApprovalStatusBadge } from './status-badges.js';
import { renderApproverCell } from './approver-cell.js';
import {
  INBOX_COL,
  inboxColAttrs,
  INBOX_TD_CLASS,
  INBOX_TD_NOWRAP_CLASS,
  INBOX_CELL_INNER_CLASS,
  INBOX_ACTION_INNER_CLASS,
} from './inbox-table-layout.js';

/**
 * HTML 이스케이프
 * @param {unknown} value
 * @returns {string}
 */
export function escapeHtml(value) {
  const div = document.createElement('div');
  div.textContent = value == null ? '' : String(value);
  return div.innerHTML;
}

/**
 * ISO 문자열을 YYYY-MM-DD 로 줄인다.
 * @param {unknown} value
 * @returns {string}
 */
export function formatInboxDate(value) {
  return String(value || '').slice(0, 10);
}

/**
 * 문서번호 셀
 * @param {unknown} approvalId
 * @returns {string}
 */
export function renderIdCell(approvalId) {
  return `<td class="${INBOX_TD_NOWRAP_CLASS} text-sm text-gray-800 dark:text-gray-200" ${inboxColAttrs(INBOX_COL.ID, '문서번호')}>
    <div class="${INBOX_CELL_INNER_CLASS} approval-inbox-td-value">${escapeHtml(approvalId)}</div>
  </td>`;
}

/**
 * 제목(상세 모달 링크) 셀
 * @param {unknown} title
 * @returns {string}
 */
export function renderTitleCell(title) {
  return `<td class="${INBOX_TD_CLASS}" ${inboxColAttrs(INBOX_COL.TITLE, '제목')}>
    <div class="${INBOX_CELL_INNER_CLASS} approval-inbox-td-value min-w-0">
      <button type="button" class="approval-detail-trigger w-full truncate text-left text-sm text-gray-900 underline-offset-2 hover:underline dark:text-white">${escapeHtml(title)}</button>
    </div>
  </td>`;
}

/**
 * 양식 코드 셀 (대기함)
 * @param {unknown} formCode
 * @returns {string}
 */
export function renderFormCodeCell(formCode) {
  return `<td class="${INBOX_TD_CLASS} text-xs text-gray-500 dark:text-gray-400" ${inboxColAttrs(INBOX_COL.FORM, '양식')}>
    <div class="${INBOX_CELL_INNER_CLASS} approval-inbox-td-value">${escapeHtml(formCode || '')}</div>
  </td>`;
}

/**
 * 기안자/신청자 셀
 * @param {unknown} name
 * @param {string} label
 * @returns {string}
 */
export function renderDrafterNameCell(name, label = '기안자') {
  return `<td class="${INBOX_TD_CLASS} text-sm text-gray-800 dark:text-gray-200" ${inboxColAttrs(INBOX_COL.DRAFTER, label)}>
    <div class="${INBOX_CELL_INNER_CLASS} approval-inbox-td-value min-w-0"><span class="truncate">${escapeHtml(name || '')}</span></div>
  </td>`;
}

/**
 * 상태 뱃지 셀
 * @param {unknown} status
 * @returns {string}
 */
export function renderStatusCell(status) {
  return `<td class="${INBOX_TD_CLASS}" ${inboxColAttrs(INBOX_COL.STATUS, '상태')}>
    <div class="${INBOX_CELL_INNER_CLASS} approval-inbox-td-value">${renderApprovalStatusBadge(String(status || ''))}</div>
  </td>`;
}

/**
 * 사유 셀. 18자 초과 시 자르고 hover 시 전체 노출.
 * @param {Record<string, unknown>} row
 * @returns {string}
 */
export function renderCommentCell(row) {
  const raw = typeof row.approverComment === 'string' ? row.approverComment.trim() : '';
  if (!raw) {
    return `<td class="${INBOX_TD_CLASS} text-xs text-gray-400 dark:text-gray-500" ${inboxColAttrs(INBOX_COL.COMMENT, '사유')}>
      <div class="${INBOX_CELL_INNER_CLASS} approval-inbox-td-value">—</div>
    </td>`;
  }
  const trunc = raw.length > 18 ? `${raw.slice(0, 18)}…` : raw;
  return `<td class="${INBOX_TD_CLASS} text-xs text-gray-600 dark:text-gray-300" title="${escapeHtml(raw)}" ${inboxColAttrs(INBOX_COL.COMMENT, '사유')}>
    <div class="${INBOX_CELL_INNER_CLASS} approval-inbox-td-value min-w-0"><span class="truncate">${escapeHtml(trunc)}</span></div>
  </td>`;
}

/**
 * 기안일/처리일 셀
 * @param {unknown} iso
 * @param {string} label
 * @returns {string}
 */
export function renderDateCell(iso, label = '기안일') {
  return `<td class="${INBOX_TD_NOWRAP_CLASS} text-xs text-gray-500 dark:text-gray-400" ${inboxColAttrs(INBOX_COL.DATE, label)}>
    <div class="${INBOX_CELL_INNER_CLASS} approval-inbox-td-value">${escapeHtml(formatInboxDate(iso))}</div>
  </td>`;
}

/**
 * 내 결재함 취소 버튼 셀
 * @param {string} actionHtml
 * @param {string} label
 * @returns {string}
 */
export function renderActionCell(actionHtml = '', label = '') {
  const emptyCls = actionHtml ? '' : ' approval-inbox-td-action--empty';
  return `<td class="${INBOX_TD_NOWRAP_CLASS} text-right${emptyCls}" ${inboxColAttrs(INBOX_COL.ACTION, label)}>
    <div class="${INBOX_ACTION_INNER_CLASS} approval-inbox-td-value">${actionHtml}</div>
  </td>`;
}

/**
 * 대기함 처리 버튼 묶음
 * @returns {string}
 */
export function renderPendingActionsCell() {
  return `<td class="${INBOX_TD_CLASS} text-right" ${inboxColAttrs(INBOX_COL.ACTION, '처리')}>
    <div class="${INBOX_ACTION_INNER_CLASS} approval-inbox-td-value flex-nowrap gap-1.5">
      <button type="button" data-act="APPROVE" class="approval-act shrink-0 rounded-lg bg-indigo-400 px-3 py-1 text-xs font-medium text-white hover:bg-indigo-500">승인</button>
      <button type="button" data-act="REJECT" class="approval-act shrink-0 rounded-lg bg-rose-200 px-3 py-1 text-xs font-medium text-rose-500 hover:bg-rose-300">반려</button>
      <button type="button" data-act="HOLD" class="approval-act shrink-0 rounded-lg bg-violet-200 px-3 py-1 text-xs font-medium text-violet-700 hover:bg-violet-300 dark:bg-violet-900/30 dark:text-violet-300">보류</button>
    </div>
  </td>`;
}

export { renderApproverCell };
