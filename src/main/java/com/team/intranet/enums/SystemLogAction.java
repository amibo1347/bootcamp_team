package com.team.intranet.enums;

/**
 * 시스템 로그 액션 타입 — "어떻게" 했는지의 추상 분류.
 *  - CREATE / UPDATE / DELETE 가 기본. 그 외 도메인 고유 행위는 OTHER 로 두고 detail 에 풀어 적는다.
 *  - 한국어 라벨은 화면 표시용. AI 요약 프롬프트에도 이 라벨이 전달된다.
 */
public enum SystemLogAction {
    CREATE("생성"),
    UPDATE("수정"),
    DELETE("삭제"),
    APPROVE("승인"),
    REJECT("거절"),
    RESET("초기화"),
    LOGIN("로그인"),
    OTHER("기타");

    private final String label;

    SystemLogAction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
