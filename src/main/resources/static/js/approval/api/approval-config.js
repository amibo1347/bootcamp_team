/**
 * 전자결재 클라이언트 설정
 * - USE_APPROVAL_MOCK: true면 네트워크 없이 Mock만 사용
 * - 실 API 연동 시 false로 두고 tryRealThenMock 경로에서 fetch 실패 시 Mock 폴백
 */

/** @type {boolean} Mock 전용 모드 (기본 true 권장 — 백엔드 API 미구현 시) */
export const USE_APPROVAL_MOCK = true;

/**
 * 실제 API 베이스 경로 (향후 컨트롤러가 생기면 여기만 조정)
 * 프로젝트 규칙: 조회는 GET, 변경은 POST
 */
export const APPROVAL_API_BASE = '/api/approval';

/** Mock 지연(ms) — 비동기 UX 확인용 */
export const MOCK_DELAY_MS = 280;

/** 페이지네이션 기본 크기 (Mock) */
export const DEFAULT_PAGE_SIZE = 10;
