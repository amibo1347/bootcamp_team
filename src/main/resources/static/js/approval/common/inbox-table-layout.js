/**
 * 전자결재 함 목록 테이블 — 행·셀·반응형 레이아웃 상수 (내 결재함 ‘전체’ 기준)
 */

/** data-col 키 — CSS 반응형·열 숨김과 매칭 */
export const INBOX_COL = {
  ID: 'id',
  TITLE: 'title',
  STATUS: 'status',
  APPROVER: 'approver',
  COMMENT: 'comment',
  DATE: 'date',
  ACTION: 'action',
  FORM: 'form',
  DRAFTER: 'drafter',
};

/**
 * 반응형 카드 레이아웃용 td/th 속성
 * @param {string} col INBOX_COL 값
 * @param {string} label 모바일 카드에 표시할 라벨
 * @returns {string}
 */
export function inboxColAttrs(col, label) {
  const safe = String(label).replace(/"/g, '&quot;');
  return `data-col="${col}" data-label="${safe}"`;
}

/** 테이블 루트 클래스 */
export const INBOX_TABLE_CLASS =
  'approval-inbox-table min-w-full divide-y divide-gray-200 dark:divide-gray-800';

/** thead th 우측 정렬 */
export const INBOX_TH_RIGHT_CLASS = 'approval-inbox-th--right';

/** tbody td 공통 — 패딩은 inbox-table-styles.css */
export const INBOX_TD_CLASS = 'align-middle';

/** 줄바꿈 방지 td */
export const INBOX_TD_NOWRAP_CLASS = 'whitespace-nowrap align-middle';

/** 단일 줄 셀 inner */
export const INBOX_CELL_INNER_CLASS = 'flex min-h-8 items-center';

/** 결재자 셀 inner */
export const INBOX_APPROVER_INNER_CLASS = 'flex min-h-8 items-center gap-2';

/** 결재자 보조 줄 */
export const INBOX_APPROVER_SUBLINE_CLASS =
  'min-h-[1.125rem] truncate text-xs leading-[1.125rem] text-gray-400 dark:text-gray-500';

/** 액션·처리 버튼 열 */
export const INBOX_ACTION_INNER_CLASS = 'flex min-h-8 items-center justify-end';
