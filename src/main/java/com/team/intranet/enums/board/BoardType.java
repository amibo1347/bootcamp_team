package com.team.intranet.enums.board;

public enum BoardType {
    NOTICE(180),       // 공지사항: 180일
    POLICY(180),      // 사내정책: 180일
    FREE(30),         // 자유게시판: 30일
    QNA(90),          // Q&A 게시판: 90일
    ANONYMOUS(30),     // 익명게시판: 30일
    ETC(30);          // 기타: 30일

    private final int retentionDays;

    BoardType(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public int getRetentionDays() {
        return retentionDays;
    }
}
