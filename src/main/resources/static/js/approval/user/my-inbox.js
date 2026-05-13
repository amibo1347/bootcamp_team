/**
 * 내 결재함 Mock 목록 컨트롤러
 * - approval-client를 통해 목록을 가져오고 상태 필터와 배지를 렌더링한다.
 */

import { listMyApprovals } from '../api/approval-client.js';
import { USER_STATUS_FILTERS, renderApprovalStatusBadge } from '../common/status-badges.js';

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
 * 내 결재함 행을 렌더링한다.
 * @param {HTMLElement} tbody
 * @param {Array<Record<string, unknown>>} items
 */
function renderRows(tbody, items) {
  tbody.innerHTML = '';
  items.forEach((row) => {
    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td class="whitespace-nowrap px-4 py-3 text-sm text-gray-800 dark:text-gray-200">${escapeHtml(row.approvalId)}</td>
      <td class="px-4 py-3 text-sm text-gray-900 dark:text-white">${escapeHtml(row.title)}</td>
      <td class="px-4 py-3">${renderApprovalStatusBadge(String(row.status || ''))}</td>
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

  /**
   * 선택된 상태 필터로 목록을 다시 조회한다.
   * @returns {Promise<void>}
   */
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
