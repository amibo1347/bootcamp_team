/**
 * 내 결재함 목록 컨트롤러
 * - approval-client를 통해 목록을 가져오고 상태 필터·페이지네이션·결재선 드롭다운을 렌더링한다.
 * - 결재자 셀은 최종(마지막 단계) 결재자만 표시. 셀 클릭 시 단계별 결재선이 오버레이로 펼쳐진다.
 * - 행의 제목 클릭 시 상세 모달을 연다.
 */

import { listMyApprovals, deleteApproval } from '../api/approval-client.js';
import {
  USER_STATUS_FILTERS,
  approvalStatusBadgeClass,
  approvalStatusLabel,
} from '../common/status-badges.js';
import {
  renderApproverDropdown,
  bindApproverDropdownToggle,
} from '../common/approver-cell.js';
import {
  renderIdCell,
  renderTitleCell,
  renderStatusCell,
  renderCommentCell,
  renderDateCell,
  renderActionCell,
  renderApproverCell,
} from '../common/inbox-table-cells.js';
import { openApprovalDetailModal } from './approval-detail-modal.js';
import { mountApprovalCombobox } from '../common/approval-combobox.js';

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
 * 취소 가능 여부 — 대기(PENDING) 상태에서만 취소할 수 있다.
 *   보류(ON_HOLD) 는 결재자가 다시 검토할 수 있는 상태이므로 신청자 취소 대상에서 제외.
 * @param {unknown} status
 * @returns {boolean}
 */
function canCancel(status) {
  const s = String(status || '').toUpperCase();
  return s === 'PENDING';
}

/**
 * 목록 행을 내 결재함(전체 필터) 스타일로 렌더링한다.
 * @param {HTMLElement} tbody
 * @param {Array<Record<string, unknown>>} items
 */
function renderRows(tbody, items) {
  tbody.innerHTML = '';
  items.forEach((row) => {
    const tr = document.createElement('tr');
    tr.dataset.approvalId = String(row.approvalId);
    const actionCell = canCancel(row.status)
      ? `<button type="button" class="approval-delete-trigger rounded-lg bg-gray-200 px-3 py-1 text-xs font-medium text-gray-700 hover:bg-gray-300 dark:bg-gray-700 dark:text-gray-200 dark:hover:bg-gray-800">취소</button>`
      : '';
    tr.innerHTML = `
      ${renderIdCell(row.approvalId)}
      ${renderTitleCell(row.title)}
      ${renderStatusCell(row.status)}
      ${renderApproverCell(row)}
      ${renderCommentCell(row)}
      ${renderDateCell(row.draftedAt)}
      ${renderActionCell(actionCell, actionCell ? '관리' : '')}`;
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
  const pagination = document.getElementById('approval-my-inbox-pagination');
  const pageInfo = document.getElementById('approval-my-inbox-page-info');
  const prevBtn = document.getElementById('approval-my-inbox-prev');
  const nextBtn = document.getElementById('approval-my-inbox-next');

  if (filter instanceof HTMLSelectElement) {
    if (filter.options.length === 0) {
      renderFilterOptions(filter);
    }
    mountApprovalCombobox(filter);
  }

  if (tbody instanceof HTMLElement) {
    bindApproverDropdownToggle(tbody);
    tbody.addEventListener('click', async (event) => {
      const target = event.target;
      if (!(target instanceof HTMLElement)) return;

      const cancelBtn = target.closest('.approval-delete-trigger');
      if (cancelBtn instanceof HTMLElement) {
        const row = cancelBtn.closest('tr');
        const id = Number(row?.dataset.approvalId);
        if (!Number.isFinite(id)) return;
        if (!window.confirm('이 결재 문서를 취소하시겠습니까?')) return;
        cancelBtn.disabled = true;
        try {
          await deleteApproval(id);
          await refresh();
        } catch (error) {
          alert(error?.message || '취소 중 오류가 발생했습니다.');
          cancelBtn.disabled = false;
        }
        return;
      }

      const trigger = target.closest('.approval-detail-trigger');
      if (!(trigger instanceof HTMLElement)) return;
      const row = trigger.closest('tr');
      const id = Number(row?.dataset.approvalId);
      if (Number.isFinite(id)) openApprovalDetailModal(id);
    });
  }

  let currentPage = 1;
  let totalPages = 1;

  function syncPagination() {
    if (!pagination) return;
    if (totalPages <= 1) {
      pagination.classList.add('hidden');
      pagination.classList.remove('flex');
      return;
    }
    pagination.classList.remove('hidden');
    pagination.classList.add('flex');
    if (pageInfo) pageInfo.textContent = `${currentPage} / ${totalPages}`;
    if (prevBtn instanceof HTMLButtonElement) prevBtn.disabled = currentPage <= 1;
    if (nextBtn instanceof HTMLButtonElement) nextBtn.disabled = currentPage >= totalPages;
  }

  async function refresh() {
    if (!tbody) return;
    const status = filter instanceof HTMLSelectElement ? filter.value : 'ALL';
    const response = await listMyApprovals({
      memberId: options.memberId ?? undefined,
      status: status === 'ALL' ? null : status,
      page: currentPage,
    });
    const items = Array.isArray(response.items) ? response.items : [];
    totalPages = Math.max(1, Number(response.totalPages) || 1);
    if (currentPage > totalPages) {
      currentPage = totalPages;
    }

    renderRows(tbody, items);
    emptyWrap?.classList.toggle('hidden', items.length > 0);
    if (count) count.textContent = `총 ${response.total ?? items.length}건`;
    syncPagination();
  }

  filter?.addEventListener('change', () => {
    currentPage = 1;
    void refresh();
  });

  prevBtn?.addEventListener('click', () => {
    if (currentPage <= 1) return;
    currentPage -= 1;
    void refresh();
  });

  nextBtn?.addEventListener('click', () => {
    if (currentPage >= totalPages) return;
    currentPage += 1;
    void refresh();
  });

  return { refresh };
}

export {
  renderApproverCell,
  renderApproverDropdown,
  bindApproverDropdownToggle,
  approvalStatusBadgeClass,
  approvalStatusLabel,
};
