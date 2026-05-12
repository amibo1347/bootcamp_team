package com.team.intranet.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Preface {
    
    ARTICLE_NEW("[새 게시글]"),
    ARTICLE_COMMENT("[새 댓글]"),
    COMMENT_REPLY("[답글]"),
    CALENDAR_ALERT("[일정 알림]");

    private final String label;
}
