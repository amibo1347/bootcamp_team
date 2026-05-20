package com.team.intranet.util;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

public class HtmlSanitizer {
    
    private static final Safelist BOARD_CONTENT_SAFELIST = createBoardSafelist();
    
    /**
     * 게시글 본문 정화
     */
    public static String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return html;
        }
        return Jsoup.clean(html, BOARD_CONTENT_SAFELIST);
    }
    
    /**
     * 게시판용 Safelist 정의
     */
    private static Safelist createBoardSafelist() {
        return Safelist.relaxed()
            // 코드 블록 (개발팀 게시판용)
            .addTags("pre", "code")
            
            // 추가 속성
            .addAttributes(":all", "class", "style")
            .addAttributes("img", "width", "height")
            .addAttributes("a", "target", "rel")
            
            // 안전한 프로토콜
            .addProtocols("a", "href", "http", "https", "mailto")
            .addProtocols("img", "src", "http", "https", "data")
            
            // 사용자 첨부 이미지를 위한 base64 허용 (또는 cid 등)
            .preserveRelativeLinks(true);
    }
}