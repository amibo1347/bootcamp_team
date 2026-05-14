/**
 * 내 결재함 목록 컨트롤러
 * - approval-client를 통해 목록을 가져오고 상태 필터·결재선 드롭다운을 렌더링한다.
 * - 결재자 셀은 최종(마지막 단계) 결재자만 표시.
 * - 셀 클릭 시 같은 셀 아래에 absolute 오버레이로 단계별 결재선을 띄움(다른 row 를 덮음).
 */

import { listMyApprovals } from '../api/approval-client.js';
import {
  USER_STATUS_FILTERS,
  approvalStatusBadgeClass,
  approvalStatusLabel,
  renderApprovalStatusBadge,
} from '../common/status-badges.js';
import {
  renderApproverCell,
  renderApproverDropdown,
  bindApproverDropdownToggle,
} from '../common/approver-cell.js';

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
 * ISO 문자열을 날짜 표시로 줄인다.
 * @param {unknown} value
 * @returns {string}
 */
function formatDate(value) {
  return String(value || '').slice(0, 10);
}

/**
 * 내 결재함 필터 옵션을 구성한다.
 * @param {HTMLSelectElement} filter
 */
function renderFilterOptions(filter) {
  filter.innerHTML = USER_STATUS_FILTERS.map(
    (option) => `<option value="${option.value}">${option.label}</option>`,
  ).join('');
}

/**
 * 사유 셀. 18자 초과 시 자르고 hover 시 전체 노출.
 */
function renderCommentCell(row) {
  const raw = typeof row.approverComment === 'string' ? row.approverComment.trim() : '';
  if (!raw) {
    return `<td class="px-4 py-3 text-xs text-gray-400 dark:text-gray-500">—</td>`;
  }
  const trunc = raw.length > 18 ? `${raw.slice(0, 18)}…` : raw;
  return `<td class="px-4 py-3 text-xs text-gray-600 dark:text-gray-300" title="${escapeHtml(raw)}">${escapeHtml(trunc)}</td>`;
}

function renderRows(tbody, items) {
  tbody.innerHTML = '';
  items.forEach((row) => {
    const tr = document.createElement('tr');
    tr.dataset.approvalId = String(row.approvalId);
    tr.innerHTML = `
      <td class="whitespace-nowrap px-4 py-3 text-sm text-gray-800 dark:text-gray-200">${escapeHtml(row.approvalId)}</td>
      <td class="px-4 py-3 text-sm text-gray-900 dark:text-white">${escapeHtml(row.title)}</td>
      <td class="px-4 py-3">${renderApprovalStatusBadge(String(row.status || ''))}</td>
      ${renderApproverCell(row)}
      ${renderCommentCell(row)}
      <td class="whitespace-nowrap px-4 py-3 text-xs text-gray-500 dark:text-gray-400">${escapeHtml(formatDate(row.draftedAt))}</td>`;
    tbody.appendChild(tr);
  });
}

/**
 * 내 결재함 컨트롤러를 초기화한다.
 * @param {{ memberId?: number|null }} options
 * @returns {{ refresh: () => Promise<void> }}
 */
export function initMyInbox(options = {}) {
  const tbody = document.getElementById('approval-my-inbox-body');
  const emptyWrap = document.getElementById('approval-my-inbox-empty');
  const filter = document.getElementById('approval-my-status-filter');
  const count = document.getElementById('approval-my-inbox-count');

  if (filter instanceof HTMLSelectElement && filter.options.length === 0) {
    renderFilterOptions(filter);
  }

  if (tbody instanceof HTMLElement) {
    bindApproverDropdownToggle(tbody);
  }

  async function refresh() {
    if (!tbody) return;
    const status = filter instanceof HTMLSelectElement ? filter.value : 'ALL';
    const response = await listMyApprovals({
      memberId: options.memberId ?? undefined,
      status: status === 'ALL' ? null : status,
      page: 1,
    });
    const items = Array.isArray(response.items) ? response.items : [];

    renderRows(tbody, items);
    emptyWrap?.classList.toggle('hidden', items.length > 0);
    if (count) count.textContent = `총 ${response.total ?? items.length}건`;
  }

  filter?.addEventListener('change', () => {
    void refresh();
  });

  return { refresh };
}

// 외부에서 helper 가 필요할 수 있어 재export (approval-main.js 의 완료함에서 동일 셀 사용)
export {
  renderApproverCell,
  renderApproverDropdown,
  bindApproverDropdownToggle,
  approvalStatusBadgeClass,
  approvalStatusLabel,
};
