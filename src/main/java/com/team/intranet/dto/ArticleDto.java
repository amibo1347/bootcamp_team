package com.team.intranet.dto;

import org.springframework.web.bind.annotation.ModelAttribute;

import com.team.intranet.entity.Article;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArticleDto {
    private Long articleId; // 게시글 ID
    private String title;   // 게시글 제목
    private String content; // 게시글 내용
    private Long boardId;   // 게시판 ID
    private Long authorId;  // 작성자 ID
    private boolean isAnonymous;    // 익명 여부
    private Long viewCount;  // 조회수
    private int commentCount;   // 댓글 수
    private String createdAt;
    private String authorName;
    private List<Long> attachmentIds;
    private String thumbnailUrl;

    private static final Pattern MD_IMG_PATTERN =
        Pattern.compile("!\\[[^\\]]*\\]\\(([^)]+)\\)");
    private static final Pattern HTML_IMG_PATTERN =
        Pattern.compile("<img[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern YOUTUBE_PATTERN =
        Pattern.compile("(?:youtube\\.com/watch\\?v=|youtu\\.be/|youtube\\.com/embed/)([\\w-]{11})");
    private static final String[] ALLOWED_THUMBNAIL_PREFIXES = {
        "/api/article-image/",
        "https://img.youtube.com/vi/"
    };

    public static ArticleDto from(Article article) {
        ArticleDto dto = new ArticleDto();
        dto.setArticleId(article.getArticleId());
        dto.setTitle(article.getTitle());
        dto.setContent(article.getContent());
        dto.setBoardId(article.getBoard() != null ? article.getBoard().getBoardId() : null);
        dto.setAuthorId(article.getAuthor() != null ? article.getAuthor().getMemberId() : null);
        dto.setAnonymous(article.isAnonymous());
        dto.setViewCount(article.getViewCount());
        dto.setCommentCount(article.getCommentCount());
        dto.setCreatedAt(article.getCreatedAt() != null ? article.getCreatedAt().toString() : null);
        dto.setAuthorName(resolveAuthorName(article));
        dto.setThumbnailUrl(extractThumbnail(article.getContent()));
        return dto;
    }

    // 작성자 표시명 결정 우선순위:
    // 1) 익명글  2) 탈퇴/해고/거절로 박제된 표시명  3) 살아있는 author 의 이름  4) 폴백
    private static String resolveAuthorName(Article article) {
        if (article.isAnonymous()) return "익명";
        String fixed = article.getAuthorDisplayName();
        if (fixed != null && !fixed.isBlank()) return fixed;
        if (article.getAuthor() != null) return article.getAuthor().getName();
        return "탈퇴 회원";
    }

    private static String extractThumbnail(String content) {
        if (content == null || content.isEmpty()) return null;

        Matcher md = MD_IMG_PATTERN.matcher(content);
        if (md.find()) {
            String url = md.group(1).trim();
            if (isAllowedThumbnailUrl(url)) return url;
        }
        Matcher html = HTML_IMG_PATTERN.matcher(content);
        if (html.find()) {
            String url = html.group(1).trim();
            if (isAllowedThumbnailUrl(url)) return url;
        }
        Matcher yt = YOUTUBE_PATTERN.matcher(content);
        if (yt.find()) {
            return "https://img.youtube.com/vi/" + yt.group(1) + "/hqdefault.jpg";
        }
        return null;
    }

    private static boolean isAllowedThumbnailUrl(String url) {
        for (String prefix : ALLOWED_THUMBNAIL_PREFIXES) {
            if (url.startsWith(prefix)) return true;
        }
        return false;
    }
}