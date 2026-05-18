package com.team.intranet.enums.member;

/**
 * SUB_ADMIN 권한 세분화.
 *  - Role.SUB_ADMIN 자체는 "어떤 관리자 메뉴든 접근할 수 있다" 정도의 상위 카테고리이고,
 *    실제 어떤 화면을 쓸 수 있는지는 직급(Position) 에 부여된 이 enum 의 집합으로 결정한다.
 *  - ADMIN / MASTER 는 모든 권한을 가진 것으로 간주하며, 이 컬렉션과 무관하게 통과한다.
 *  - 권한 관리 페이지는 ADMIN 만 접근/수정 가능.
 */
public enum SubAdminPermission {
    /** 가입 승인 대기 처리 (/admin/waitingList) */
    WAITING_APPROVAL("승인 대기"),
    /** 조직도 구성 / 회원 관리 (/admin/memberList) */
    MEMBER_MANAGEMENT("조직도 구성"),
    /** 부서 관리 (/admin/dept/list). 조직도 구성의 "신규 부서" 진입도 이 권한 필요. */
    DEPT_MANAGEMENT("부서 관리"),
    /** 직급 관리 (/admin/position/list). 조직도 구성의 "신규 직급" 진입도 이 권한 필요. */
    POSITION_MANAGEMENT("직급 관리"),
    /** 게시판 관리 (/admin/board/list) */
    BOARD_MANAGEMENT("게시판 관리"),
    /** 전자결재 양식 관리 (/approval/templates) */
    APPROVAL_TEMPLATE_MANAGEMENT("전자결재 양식 관리"),
    /**
     * 통합 휴지통 전체 조회 (/subAdmin/unified-trash).
     * ※ 메뉴 진입은 모든 회원에게 열려 있고, 일반 사용자는 본인 작성 글의 삭제분만 볼 수 있다.
     *   이 권한이 있는 회원은 회사 전체 삭제 글을 볼 수 있다.
     */
    TRASH_MANAGEMENT("통합 휴지통");

    private final String label;

    SubAdminPermission(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
