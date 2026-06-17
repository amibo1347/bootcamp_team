/**
 * 결재 상세 모달
 * - GET /api/approval/{id} 응답을 받아 헤더 + 양식별 본문 + 결재선을 보여준다.
 * - 양식별 분기: VACATION / GENERIC / EXPENSE (그 외는 본문 영역 생략).
 * - DOM 은 1회만 주입 (모달 wrap idempotent).
 */

import { getApprovalDetail } from '../api/approval-client.js';
import {
  approvalStatusBadgeClass,
  approvalStatusLabel,
} from '../common/status-badges.js';

let modalInjected = false;

function esc(value) {
  const div = document.createElement('div');
  div.textContent = value == null ? '' : String(value);
  return div.innerHTML;
}

function formatDateTime(value) {
  return String(value || '').slice(0, 16).replace('T', ' ');
}

function formatDate(value) {
  return String(value || '').slice(0, 10);
}

function formatAmount(value) {
  const n = Number(value);
  if (!Number.isFinite(n)) return '—';
  return n.toLocaleString('ko-KR') + ' 원';
}

/**
 * 모달 DOM을 body에 1회 주입.
 */
function ensureModal() {
  if (modalInjected) return;
  modalInjected = true;

  const wrap = document.createElement('div');
  wrap.innerHTML = `
    <div id="approvalDetailModal"
         class="fixed inset-0 z-99999 items-center justify-center overflow-y-auto p-4 sm:p-6"
         style="display:none"
         role="dialog" aria-modal="true" aria-labelledby="approvalDetailModalLabel">
      <div data-approval-detail-dim class="fixed inset-0 z-0 bg-gray-900/40 backdrop-blur-sm dark:bg-gray-950/60"></div>
      <div class="relative z-10 flex w-full max-w-3xl flex-col overflow-hidden rounded-2xl border border-stroke bg-white shadow-2xl ring-1 ring-black/5 dark:border-strokedark dark:bg-boxdark dark:ring-white/10"
           style="max-height: min(92vh, 820px)">

        <header class="flex shrink-0 items-start justify-between border-b border-gray-100 px-5 py-4 sm:px-6 sm:py-5 dark:border-gray-800">
          <div class="min-w-0">
            <h2 id="approvalDetailModalLabel" class="text-lg font-semibold text-gray-900 dark:text-white sm:text-xl">
              결재 상세
            </h2>
            <p id="approval-detail-subtitle" class="mt-1 text-sm leading-relaxed text-gray-500 dark:text-gray-400">
              불러오는 중…
            </p>
          </div>
          <button type="button" data-approval-detail-close
                  class="rounded-full p-1 text-gray-400 transition hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-white/5"
                  aria-label="닫기">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="h-5 w-5" aria-hidden="true">
              <path d="M6.28 5.22a.75.75 0 00-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 101.06 1.06L10 11.06l3.72 3.72a.75.75 0 101.06-1.06L11.06 10l3.72-3.72a.75.75 0 00-1.06-1.06L10 8.94 6.28 5.22z" />
            </svg>
          </button>
        </header>

        <div class="min-h-0 flex-1 overflow-y-auto px-5 py-5 sm:px-6 custom-scrollbar">
          <section id="approval-detail-content" class="space-y-5">
            <p class="text-sm text-gray-500 dark:text-gray-400">불러오는 중…</p>
          </section>
        </div>

        <footer class="flex shrink-0 flex-wrap items-center justify-end gap-3 border-t border-gray-100 bg-gray-50/90 px-5 py-4 backdrop-blur-sm dark:border-gray-800 dark:bg-gray-900/80 sm:px-6">
          <button type="button" data-approval-detail-close
                  class="rounded-xl border border-gray-300 bg-white px-5 py-2.5 text-sm font-medium text-gray-700 shadow-sm transition hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-200 dark:hover:bg-gray-700">
            닫기
          </button>
        </footer>

      </div>
    </div>
  `;
  document.body.appendChild(wrap.firstElementChild);

  const modal = document.getElementById('approvalDetailModal');
  modal?.querySelectorAll('[data-approval-detail-close], [data-approval-detail-dim]').forEach((el) => {
    el.addEventListener('click', closeModal);
  });
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') closeModal();
  });
}

function closeModal() {
  const modal = document.getElementById('approvalDetailModal');
  if (modal instanceof HTMLElement) modal.style.display = 'none';
}

/**
 * 본문(휴가)
 */
function renderVacationBody(v) {
  if (!v) return '';
  const typeLabel = v.vacationTypeLabel || v.vacationType || '—';
  return `
    <div>
      <h3 class="mb-2 text-xs font-semibold uppercase tracking-wider text-gray-400">휴가 정보</h3>
      <dl class="grid grid-cols-2 gap-3 rounded-xl border border-gray-200 bg-white p-4 text-sm dark:border-gray-700 dark:bg-white/[0.03]">
        <div><dt class="text-xs text-gray-500">유형</dt><dd class="font-medium text-gray-900 dark:text-white">${esc(typeLabel)}</dd></div>
        <div><dt class="text-xs text-gray-500">단위</dt><dd class="font-medium text-gray-900 dark:text-white">${esc(v.halfDayLabel || '종일')}</dd></div>
        <div><dt class="text-xs text-gray-500">일수</dt><dd class="font-medium text-gray-900 dark:text-white">${esc(v.totalDays ?? v.days ?? '—')}</dd></div>
        <div><dt class="text-xs text-gray-500">시작일</dt><dd class="font-medium text-gray-900 dark:text-white">${esc(formatDate(v.startDate))}</dd></div>
        <div><dt class="text-xs text-gray-500">종료일</dt><dd class="font-medium text-gray-900 dark:text-white">${esc(formatDate(v.endDate))}</dd></div>
        ${v.reason ? `<div class="col-span-2"><dt class="text-xs text-gray-500">사유</dt><dd class="whitespace-pre-wrap text-sm text-gray-800 dark:text-gray-200">${esc(v.reason)}</dd></div>` : ''}
      </dl>
    </div>`;
}

/**
 * 본문(지출)
 */
function renderExpenseBody(e) {
  if (!e) return '';
  return `
    <div>
      <h3 class="mb-2 text-xs font-semibold uppercase tracking-wider text-gray-400">지출 정보</h3>
      <dl class="grid grid-cols-2 gap-3 rounded-xl border border-gray-200 bg-white p-4 text-sm dark:border-gray-700 dark:bg-white/[0.03]">
        <div><dt class="text-xs text-gray-500">금액</dt><dd class="font-medium text-gray-900 dark:text-white">${esc(formatAmount(e.amount))}</dd></div>
        <div><dt class="text-xs text-gray-500">분류</dt><dd class="font-medium text-gray-900 dark:text-white">${esc(e.category || '—')}</dd></div>
        <div><dt class="text-xs text-gray-500">지출일</dt><dd class="font-medium text-gray-900 dark:text-white">${esc(formatDate(e.spentAt))}</dd></div>
        ${e.description ? `<div class="col-span-2"><dt class="text-xs text-gray-500">상세</dt><dd class="whitespace-pre-wrap text-sm text-gray-800 dark:text-gray-200">${esc(e.description)}</dd></div>` : ''}
      </dl>
    </div>`;
}

/**
 * 본문(일반 기안)
 */
function renderGenericBody(g) {
  if (!g) return '';
  return `
    <div>
      <h3 class="mb-2 text-xs font-semibold uppercase tracking-wider text-gray-400">기안 본문</h3>
      <div class="whitespace-pre-wrap rounded-xl border border-gray-200 bg-white p-4 text-sm text-gray-800 dark:border-gray-700 dark:bg-white/[0.03] dark:text-gray-200">${esc(g.content || '')}</div>
    </div>`;
}

/**
 * 본문(B안 동적). schemaSnapshot 으로 그려야 양식이 후에 수정·삭제돼도 결재 본문이 원형 유지된다.
 * 12 컬럼 그리드 (admin 캔버스에서 박은 row/col/span 좌표 그대로 재현). 모바일은 CSS 가 1 컬럼으로 stack.
 */
function renderDynamicBody(schemaJson, values) {
  let schema = [];
  try {
    const parsed = JSON.parse(schemaJson);
    if (Array.isArray(parsed)) schema = parsed;
  } catch (err) {
    return `<p class="text-xs text-rose-500">양식 스냅샷을 읽을 수 없습니다.</p>`;
  }
  if (schema.length === 0) return '';

  const vmap = (values && typeof values === 'object') ? values : {};
  const rows = schema.map((field) => {
    const key = String(field?.key || '');
    const label = String(field?.label || key);
    const type = String(field?.type || 'text');
    const vals = Array.isArray(vmap[key]) ? vmap[key] : [];

    // 좌표 — row/col/span 우선, 없으면 옛 width 호환
    const span = Number.isFinite(field?.span)
      ? Math.max(1, Math.min(12, Number(field.span)))
      : (field?.width === 'half' ? 6 : 12);
    const col = Number.isFinite(field?.col)
      ? Math.max(1, Math.min(12, Number(field.col)))
      : 1;
    const rowStyle = Number.isFinite(field?.row) ? `grid-row: ${Number(field.row)};` : '';
    const gridStyle = `grid-column: ${col} / span ${span}; ${rowStyle}`;

    let displayed;
    if (vals.length === 0) {
      displayed = '<span class="text-gray-400">—</span>';
    } else if (type === 'checkbox') {
      displayed = esc(vals[0] === 'true' ? '예' : '아니오');
    } else if (type === 'multi-select' || vals.length > 1) {
      displayed = esc(vals.join(', '));
    } else {
      displayed = esc(vals[0]);
    }

    const computedBadge = type === 'computed'
      ? '<span class="ml-1 inline-flex items-center rounded bg-purple-100 px-1.5 py-0.5 text-[10px] font-medium text-purple-700 dark:bg-purple-500/15 dark:text-purple-300">자동</span>'
      : '';

    return `
      <div style="${gridStyle}" class="rounded-xl border border-gray-200 bg-white p-3 dark:border-gray-700 dark:bg-white/[0.03]">
        <dt class="text-xs text-gray-500">${esc(label)}${computedBadge}</dt>
        <dd class="mt-1 ${type === 'textarea' ? 'whitespace-pre-wrap' : ''} text-sm font-medium text-gray-900 dark:text-white">${displayed}</dd>
      </div>`;
  }).join('');

  return `
    <div>
      <h3 class="mb-2 text-xs font-semibold uppercase tracking-wider text-gray-400">본문</h3>
      <dl class="approval-detail-dynamic-body">
        ${rows}
      </dl>
    </div>`;
}

/**
 * 첨부파일 목록.
 */
function renderAttachments(attachments) {
  if (!Array.isArray(attachments) || attachments.length === 0) return '';
  const items = attachments.map((att) => {
    const n = Number(att.size);
    const sizeText = Number.isFinite(n) ? `${Math.ceil(n / 1024)} KB` : '';
    const url = att.downloadUrl || `/api/approval-attachment/${att.id}`;
    return `
      <li class="flex items-center justify-between gap-3 rounded-xl border border-gray-200 bg-white px-3 py-2 dark:border-gray-700 dark:bg-white/[0.03]">
        <span class="min-w-0 truncate text-sm text-gray-800 dark:text-gray-200">${esc(att.filename)}${sizeText ? ` <span class="text-xs text-gray-400">(${sizeText})</span>` : ''}</span>
        <a href="${esc(url)}" download
           class="shrink-0 rounded-md bg-brand-50 px-2.5 py-1 text-xs font-medium text-brand-600 hover:bg-brand-100 dark:bg-brand-500/15 dark:text-brand-300">
          다운로드
        </a>
      </li>`;
  }).join('');
  return `
    <div>
      <h3 class="mb-2 text-xs font-semibold uppercase tracking-wider text-gray-400">첨부파일</h3>
      <ul class="flex flex-col gap-2">${items}</ul>
    </div>`;
}

/**
 * 결재선 표시.
 */
function renderApprovalLine(approvers, currentLevel, overallStatus) {
  if (!Array.isArray(approvers) || approvers.length === 0) {
    return `<p class="text-xs text-gray-400">결재선 정보가 없습니다.</p>`;
  }
  const isOngoing = overallStatus === 'PENDING' || overallStatus === 'IN_PROGRESS';
  const cards = approvers.map((a, idx) => {
    const isFinal = idx === approvers.length - 1;
    const isCurrent = isOngoing && currentLevel != null && Number(a.level) === Number(currentLevel);
    const lineStatus = String(a.status || '');
    const meta = [a.deptName, a.positionName].filter(Boolean).join(' / ');
    const stageBadgeCls = isCurrent
      ? 'bg-brand-50 text-brand-700 dark:bg-brand-950 dark:text-brand-200'
      : 'bg-gray-100 text-gray-600 dark:bg-white/[0.05] dark:text-gray-300';
    const stageText = `${a.level}단계${isFinal ? ' · 최종' : ''}${isCurrent ? ' · 진행' : ''}`;
    return `
      <div class="flex items-center gap-3 rounded-xl border border-gray-200 bg-white px-3 py-2 dark:border-gray-700 dark:bg-white/[0.03]">
        <span class="inline-flex shrink-0 items-center justify-center rounded-full px-2 py-0.5 text-xs font-medium ${stageBadgeCls}" style="min-width: 7rem; text-align: center;">${esc(stageText)}</span>
        <div class="min-w-0 flex-1">
          <div class="truncate text-sm font-medium text-gray-900 dark:text-white">${esc(a.name || '—')}</div>
          <div class="truncate text-xs text-gray-500 dark:text-gray-400">${esc(meta || '—')}</div>
        </div>
        <span class="inline-flex shrink-0 rounded-full px-2 py-0.5 text-xs font-medium ${approvalStatusBadgeClass(lineStatus)}">${esc(approvalStatusLabel(lineStatus))}</span>
      </div>`;
  }).join('');
  return `<div class="flex flex-col gap-2">${cards}</div>`;
}

/**
 * 상세 응답을 모달에 렌더.
 */
function renderDetail(detail) {
  const subtitle = document.getElementById('approval-detail-subtitle');
  if (subtitle) {
    const drafter = [detail.drafterName, detail.drafterDeptName, detail.drafterPositionName]
      .filter(Boolean).join(' · ');
    subtitle.textContent = `${detail.formName || detail.formCode || ''} · 기안자: ${drafter || '—'}`;
  }

  // B안: schemaSnapshot 이 있으면 동적 본문 (양식 후속 변경과 무관). 아니면 기존 fixed 본문 분기.
  const code = String(detail.formCode || '').toUpperCase();
  const isDynamic = !!(detail.schemaSnapshot && String(detail.schemaSnapshot).trim());
  const body = isDynamic
    ? renderDynamicBody(detail.schemaSnapshot, detail.dynamicValues)
    : code === 'VACATION' ? renderVacationBody(detail.vacation)
    : code === 'EXPENSE' ? renderExpenseBody(detail.expense)
    : code === 'GENERIC' ? renderGenericBody(detail.generic)
    : '';

  const status = String(detail.status || '');
  const commentBlock = detail.approverComment
    ? `<div>
         <h3 class="mb-2 text-xs font-semibold uppercase tracking-wider text-gray-400">결재자 의견</h3>
         <p class="whitespace-pre-wrap rounded-xl border border-gray-200 bg-gray-50 p-4 text-sm text-gray-800 dark:border-gray-700 dark:bg-white/[0.03] dark:text-gray-200">${esc(detail.approverComment)}</p>
       </div>`
    : '';

  const content = document.getElementById('approval-detail-content');
  if (content) {
    content.innerHTML = `
      <div>
        <h3 class="mb-2 text-xs font-semibold uppercase tracking-wider text-gray-400">결재 정보</h3>
        <dl class="grid grid-cols-2 gap-3 rounded-xl border border-gray-200 bg-white p-4 text-sm dark:border-gray-700 dark:bg-white/[0.03]">
          <div><dt class="text-xs text-gray-500">문서번호</dt><dd class="font-medium text-gray-900 dark:text-white">${esc(detail.approvalId)}</dd></div>
          <div><dt class="text-xs text-gray-500">상태</dt><dd><span class="inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${approvalStatusBadgeClass(status)}">${esc(approvalStatusLabel(status))}</span></dd></div>
          <div class="col-span-2"><dt class="text-xs text-gray-500">제목</dt><dd class="font-medium text-gray-900 dark:text-white">${esc(detail.title || '')}</dd></div>
          <div><dt class="text-xs text-gray-500">기안일</dt><dd class="text-gray-700 dark:text-gray-200">${esc(formatDateTime(detail.draftedAt))}</dd></div>
          <div><dt class="text-xs text-gray-500">처리일</dt><dd class="text-gray-700 dark:text-gray-200">${esc(detail.processedAt ? formatDateTime(detail.processedAt) : '—')}</dd></div>
        </dl>
      </div>
      ${body}
      ${renderAttachments(detail.attachments)}
      <div>
        <h3 class="mb-2 text-xs font-semibold uppercase tracking-wider text-gray-400">결재선</h3>
        ${renderApprovalLine(detail.approvers, detail.currentLevel, status)}
      </div>
      ${commentBlock}
    `;
  }
}

/**
 * 결재 상세 모달 오픈.
 * @param {number} approvalId
 */
export async function openApprovalDetailModal(approvalId) {
  ensureModal();

  const modal = document.getElementById('approvalDetailModal');
  if (modal instanceof HTMLElement) modal.style.display = 'flex';

  const content = document.getElementById('approval-detail-content');
  if (content) {
    content.innerHTML = `<p class="text-sm text-gray-500 dark:text-gray-400">불러오는 중…</p>`;
  }
  const subtitle = document.getElementById('approval-detail-subtitle');
  if (subtitle) subtitle.textContent = '불러오는 중…';

  try {
    const detail = await getApprovalDetail(approvalId);
    renderDetail(detail || {});
  } catch (error) {
    console.error('[approval-detail-modal] 상세 로드 실패', error);
    if (content) {
      content.innerHTML = `<p class="text-sm text-red-500">결재 정보를 불러오지 못했습니다.</p>`;
    }
    if (subtitle) subtitle.textContent = '오류';
  }
}
