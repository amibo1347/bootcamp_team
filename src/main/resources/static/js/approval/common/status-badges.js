/**
 * 전자결재 상태 배지 공통 유틸
 * - 서버 enum 기준 상태는 PENDING, APPROVED, REJECTED만 사용한다.
 * - ON_HOLD는 관리자 Mock 데모에서만 보조로 표시한다.
 */

/** @typedef {'PENDING'|'APPROVED'|'REJECTED'|'ON_HOLD'} ApprovalDisplayStatus */

/** 내 결재함 필터 — ON_HOLD는 Mock 데모 전용(서버 enum 없음) */
export const USER_STATUS_FILTERS = [
  { value: 'ALL', label: '전체' },
  { value: 'PENDING', label: '대기' },
  { value: 'APPROVED', label: '승인' },
  { value: 'REJECTED', label: '반려' },
  { value: 'ON_HOLD', label: '보류(M)' },
];

/**
 * 상태값을 화면 라벨로 변환한다.
 * @param {string} status
 * @returns {string}
 */
export function approvalStatusLabel(status) {
  const labels = {
    PENDING: '대기',
    APPROVED: '승인',
    REJECTED: '반려',
    ON_HOLD: '보류(M)',
  };
  return labels[status] || status;
}

/**
 * 상태별 Tailwind 배지 클래스를 반환한다.
 * @param {string} status
 * @returns {string}
 */
export function approvalStatusBadgeClass(status) {
  switch (status) {
    case 'PENDING':
      return 'bg-amber-100 text-amber-800 dark:bg-amber-950/40 dark:text-amber-200';
    case 'APPROVED':
      return 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-200';
    case 'REJECTED':
      return 'bg-red-100 text-red-800 dark:bg-red-950/40 dark:text-red-200';
    case 'ON_HOLD':
      return 'bg-violet-100 text-violet-800 dark:bg-violet-950/40 dark:text-violet-200';
    default:
      return 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300';
  }
}

/**
 * 표 셀에 삽입할 상태 배지 HTML을 만든다.
 * @param {string} status
 * @returns {string}
 */
export function renderApprovalStatusBadge(status) {
  return `<span class="inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${approvalStatusBadgeClass(status)}">${approvalStatusLabel(status)}</span>`;
}
