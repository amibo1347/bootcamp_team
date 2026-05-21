/**
 * 결재자 셀 + 단계별 드롭다운 (오버레이) — 내 결재함·완료함 공통 컴포넌트.
 * - 기본: 최종(마지막 단계) 결재자만 표시
 * - 셀 클릭 시 같은 td 의 absolute panel 토글. 다른 row 들을 덮어 표시.
 * - "진행" 라벨은 결재 상태가 PENDING/IN_PROGRESS 일 때만 노출.
 */

import {
  approvalStatusBadgeClass,
  approvalStatusLabel,
} from './status-badges.js';
import {
  INBOX_COL,
  inboxColAttrs,
  INBOX_TD_CLASS,
  INBOX_APPROVER_INNER_CLASS,
  INBOX_APPROVER_SUBLINE_CLASS,
} from './inbox-table-layout.js';

function escapeHtml(value) {
  const div = document.createElement('div');
  div.textContent = value == null ? '' : String(value);
  return div.innerHTML;
}

/** 프로필 이미지 IMG 태그 (memberId 기반). 빈 ID 면 빈 문자열. */
export function avatarImg(memberId, sizeCls = 'h-8 w-8') {
  if (memberId == null) return '';
  return `<img src="/api/member/${encodeURIComponent(String(memberId))}/profileImg" alt="" class="${sizeCls} shrink-0 rounded-full object-cover ring-1 ring-gray-200 dark:ring-gray-700" />`;
}

/**
 * 아바타 슬롯 — 이미지가 없어도 행 높이를 맞추기 위해 빈 32px 영역을 둔다.
 * @param {unknown} memberId
 * @param {string} sizeCls
 * @returns {string}
 */
function avatarSlot(memberId, sizeCls = 'h-8 w-8') {
  const img = avatarImg(memberId, sizeCls);
  if (img) return img;
  return `<span class="${sizeCls} shrink-0" aria-hidden="true"></span>`;
}

const CHEVRON_SVG = `
  <svg class="ml-1 h-4 w-4 shrink-0 text-gray-400 dark:text-gray-500" data-approver-chevron viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
    <path fill-rule="evenodd" d="M5.23 7.21a.75.75 0 011.06.02L10 11.06l3.71-3.83a.75.75 0 111.08 1.04l-4.25 4.39a.75.75 0 01-1.08 0L5.21 8.27a.75.75 0 01.02-1.06z" clip-rule="evenodd" />
  </svg>`;

/**
 * 결재자 td. approvers 가 2명 이상이면 클릭 가능 + chevron 표시.
 * @param {Record<string, any>} row
 * @returns {string}
 */
export function renderApproverCell(row) {
  const approvers = Array.isArray(row.approvers) ? row.approvers : [];
  const last = approvers.length > 0 ? approvers[approvers.length - 1] : null;

  // 백엔드가 approvers 안 채워줬을 경우 fallback (현재 단계 결재자 cache)
  const id = last?.memberId ?? row.approverMemberId ?? null;
  const name = last?.name ?? row.approverName ?? '';
  const deptName = last?.deptName ?? '';
  const positionName = last?.positionName ?? '';
  const meta = [deptName, positionName].filter(Boolean).join(' / ');
  const hasMulti = approvers.length > 1;

  const stageLabel = approvers.length > 1
    ? `최종 (${approvers.length}단계 중 ${approvers.length})`
    : (approvers.length === 1 ? '단일 결재' : '');

  const subLine = stageLabel
    ? `${escapeHtml(stageLabel)}${meta ? ` · ${escapeHtml(meta)}` : ''}`
    : (meta ? escapeHtml(meta) : '&#8203;');

  const cellCls = hasMulti
    ? 'cursor-pointer select-none hover:bg-gray-50 dark:hover:bg-white/[0.03]'
    : '';

  return `
    <td class="${INBOX_TD_CLASS} approval-inbox-td-approver ${cellCls}"
        style="position: relative;"
        ${inboxColAttrs(INBOX_COL.APPROVER, '결재자')}
        ${hasMulti ? 'data-approver-toggle="1"' : ''}>
      <div class="${INBOX_APPROVER_INNER_CLASS} approval-inbox-td-value">
        ${avatarSlot(id)}
        <div class="min-w-0 flex-1">
          <div class="truncate text-sm font-medium leading-5 text-gray-900 dark:text-white">${escapeHtml(name)}</div>
          <div class="${INBOX_APPROVER_SUBLINE_CLASS}">${subLine}</div>
        </div>
        ${hasMulti ? CHEVRON_SVG : '<span class="ml-1 h-4 w-4 shrink-0" aria-hidden="true"></span>'}
      </div>
      ${hasMulti ? renderApproverDropdown(row) : ''}
    </td>`;
}

/**
 * 단계별 결재선 카드 목록(오버레이 패널). td 안에 absolute 로 위치.
 * @param {Record<string, any>} row
 * @returns {string}
 */
export function renderApproverDropdown(row) {
  const approvers = Array.isArray(row.approvers) ? row.approvers : [];
  if (approvers.length === 0) return '';

  const overallStatus = String(row.status || '');
  const isOngoing = overallStatus === 'PENDING' || overallStatus === 'IN_PROGRESS';
  const currentLevel = typeof row.currentLevel === 'number' ? row.currentLevel : null;

  const cards = approvers.map((a, idx) => {
    const isFinal = idx === approvers.length - 1;
    const isCurrent = isOngoing && currentLevel != null && Number(a.level) === currentLevel;
    const meta = [a.deptName, a.positionName].filter(Boolean).join(' / ');
    const lineStatus = String(a.status || '');
    const stageBadgeCls = isCurrent
      ? 'bg-brand-50 text-brand-700 dark:bg-brand-950 dark:text-brand-200'
      : 'bg-gray-100 text-gray-600 dark:bg-white/[0.05] dark:text-gray-300';
    const stageText = `${a.level}단계${isFinal ? ' · 최종' : ''}${isCurrent ? ' · 진행' : ''}`;
    // chip 은 고정 너비로 — 모든 카드에서 chip 우측 경계가 일치 → 사진들이 세로 일직선
    // 사진 좌측에는 row 마다 동일 위치의 세로 구분선 (w-px) 추가
    return `
      <div class="flex items-center gap-3 rounded-xl border border-gray-200 bg-white px-3 py-2 dark:border-gray-700 dark:bg-white/[0.03]">
        <span class="inline-flex shrink-0 items-center justify-center rounded-full px-2 py-0.5 text-xs font-medium ${stageBadgeCls}"
              style="width: 7rem; text-align: center;">${escapeHtml(stageText)}</span>
        <span class="block shrink-0 bg-gray-200 dark:bg-gray-700" style="width: 1px; height: 2.25rem;" aria-hidden="true"></span>
        ${avatarImg(a.memberId, 'h-9 w-9')}
        <div class="min-w-0 flex-1">
          <div class="truncate text-sm font-medium text-gray-900 dark:text-white">${escapeHtml(a.name || '')}</div>
          <div class="truncate text-xs text-gray-500 dark:text-gray-400">${escapeHtml(meta || '—')}</div>
        </div>
        <span class="inline-flex shrink-0 rounded-full px-2 py-0.5 text-xs font-medium ${approvalStatusBadgeClass(lineStatus)}">${escapeHtml(approvalStatusLabel(lineStatus))}</span>
      </div>`;
  }).join('');

  // 결재자 컬럼과 비슷한 가로폭, 다른 row 들을 덮어 표시.
  // inline style 사용: Tailwind 임의 값(min-w-[...]) 빌드 의존 없이 항상 동작.
  return `
    <div data-approver-dropdown
         class="hidden rounded-xl border border-gray-200 bg-white p-3 shadow-lg dark:border-gray-700 dark:bg-boxdark"
         style="position: absolute; left: 0; top: 100%; min-width: 100%; width: 22rem; max-height: min(22rem, calc(100vh - 2rem)); overflow-y: auto; z-index: 30;">
      <div class="mb-2 text-xs font-semibold uppercase tracking-wider text-gray-400 dark:text-gray-500">결재선</div>
      <div class="flex flex-col gap-2">
        ${cards}
      </div>
    </div>`;
}

/** 함 목록 테이블: overflow 에 가리지 않도록 fixed 배치 사용 (내·대기·완료 공통) */
const INBOX_TABLE_ROOT_SELECTOR = '.approval-inbox-table-root';

/**
 * 열린 결재선 팝업을 뷰포트 기준 fixed 로 배치한다. (내 결재함 전용)
 * @param {HTMLElement} panel
 * @param {HTMLElement} td
 */
function positionMyInboxDropdown(panel, td) {
  panel.style.position = 'fixed';
  panel.style.width = '22rem';
  panel.style.minWidth = '';
  panel.style.maxHeight = 'min(22rem, calc(100vh - 2rem))';
  panel.style.zIndex = '99950';

  const gap = 4;
  const anchor = td.getBoundingClientRect();
  const panelHeight = panel.offsetHeight;
  const panelWidth = panel.offsetWidth;
  const viewportPad = 8;

  let top = anchor.bottom + gap;
  if (top + panelHeight > window.innerHeight - viewportPad) {
    top = Math.max(viewportPad, anchor.top - panelHeight - gap);
  }

  let left = anchor.left;
  if (left + panelWidth > window.innerWidth - viewportPad) {
    left = Math.max(viewportPad, window.innerWidth - panelWidth - viewportPad);
  }

  panel.style.top = `${top}px`;
  panel.style.left = `${left}px`;
}

/**
 * fixed 배치 스타일을 제거하고 td 내부 absolute 기본값으로 되돌린다.
 * @param {HTMLElement} panel
 */
function resetMyInboxDropdownPosition(panel) {
  panel.classList.remove('approval-approver-dropdown--open');
  panel.style.position = '';
  panel.style.top = '';
  panel.style.left = '';
  panel.style.width = '';
  panel.style.minWidth = '';
  panel.style.zIndex = '';
  panel.style.maxHeight = '';
}

/**
 * 내 결재함에서 열린 드롭다운 위치를 스크롤·리사이즈 시 갱신한다.
 */
function syncOpenMyInboxDropdowns() {
  document.querySelectorAll(`${INBOX_TABLE_ROOT_SELECTOR} [data-approver-dropdown]:not(.hidden)`).forEach((node) => {
    if (!(node instanceof HTMLElement)) return;
    const td = node.closest('td[data-approver-toggle="1"]');
    if (td instanceof HTMLElement) positionMyInboxDropdown(node, td);
  });
}

/**
 * tbody 에 위임된 클릭 핸들러를 부착. 같은 셀의 dropdown 토글 + 다른 dropdown 자동 닫기.
 * 외부 클릭으로 닫는 처리는 document 레벨로 한 번만 등록.
 * @param {HTMLElement} tbody
 */
export function bindApproverDropdownToggle(tbody) {
  tbody.addEventListener('click', (event) => {
    const target = event.target;
    if (!(target instanceof HTMLElement)) return;

    // 드롭다운 내부 클릭은 무시 (셀 토글로 닫히지 않게)
    if (target.closest('[data-approver-dropdown]')) return;

    const td = target.closest('td[data-approver-toggle="1"]');
    if (!(td instanceof HTMLElement)) return;

    const panel = td.querySelector('[data-approver-dropdown]');
    if (!(panel instanceof HTMLElement)) return;

    const willOpen = panel.classList.contains('hidden');
    const useMyInboxFixed = panel.closest(INBOX_TABLE_ROOT_SELECTOR) != null;
    // 다른 열려있던 드롭다운 닫기
    document.querySelectorAll('[data-approver-dropdown]').forEach((d) => {
      if (d === panel) return;
      d.classList.add('hidden');
      if (d instanceof HTMLElement && d.closest(INBOX_TABLE_ROOT_SELECTOR)) {
        resetMyInboxDropdownPosition(d);
      }
    });
    panel.classList.toggle('hidden', !willOpen);

    if (useMyInboxFixed) {
      if (willOpen) {
        panel.classList.add('approval-approver-dropdown--open');
        positionMyInboxDropdown(panel, td);
      } else {
        resetMyInboxDropdownPosition(panel);
      }
    }

    const chevron = td.querySelector('[data-approver-chevron]');
    if (chevron instanceof SVGElement) {
      chevron.style.transform = willOpen ? 'rotate(180deg)' : 'rotate(0deg)';
    }
  });

  // 한 번만 등록 — 페이지 전역에서 외부 클릭 시 모든 드롭다운 닫기
  if (!document.body.dataset.approverDropdownOutsideBound) {
    document.body.dataset.approverDropdownOutsideBound = '1';
    document.addEventListener('click', (event) => {
      const t = event.target;
      if (!(t instanceof HTMLElement)) return;
      if (t.closest('td[data-approver-toggle="1"]')) return;
      if (t.closest('[data-approver-dropdown]')) return;
      document.querySelectorAll('[data-approver-dropdown]').forEach((d) => {
        d.classList.add('hidden');
        if (d instanceof HTMLElement && d.closest(INBOX_TABLE_ROOT_SELECTOR)) {
          resetMyInboxDropdownPosition(d);
        }
      });
      document.querySelectorAll('[data-approver-chevron]').forEach((c) => {
        if (c instanceof SVGElement) c.style.transform = 'rotate(0deg)';
      });
    });
  }

  // 내 결재함: 스크롤·리사이즈 시 fixed 팝업 위치 동기화 (1회만 등록)
  if (!document.body.dataset.approverMyInboxPositionBound) {
    document.body.dataset.approverMyInboxPositionBound = '1';
    window.addEventListener('scroll', syncOpenMyInboxDropdowns, true);
    window.addEventListener('resize', syncOpenMyInboxDropdowns);
  }
}
