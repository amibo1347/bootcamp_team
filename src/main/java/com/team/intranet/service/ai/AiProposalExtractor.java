package com.team.intranet.service.ai;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * LLM 응답 텍스트에서 액션 제안 JSON 블록을 추출하고, 본문에서는 제거한다.
 *
 *  허용 형식 (대소문자 무관, 앞뒤 공백 무관):
 *      ```json:calendar
 *      { "title": "...", ... }
 *      ```
 *      ```json:leave
 *      { "vacationType": "연차", ... }
 *      ```
 *
 *  추출 우선순위: 첫 번째 블록만 사용 (한 메시지에 하나의 제안).
 */
@Component
public class AiProposalExtractor {

    /** ```json:{type}  {...}  ``` 블록. multiline + DOTALL.
     *  지원 type: calendar / calendar_update / calendar_delete / leave */
    private static final Pattern BLOCK_RE = Pattern.compile(
        "```\\s*json:(?<type>calendar_update|calendar_delete|calendar|leave)\\s*\\n(?<body>.*?)\\n```",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private final ObjectMapper mapper = new ObjectMapper();

    /** 추출 결과. proposalJson 은 type 필드를 포함한 정규화된 JSON. */
    public record Extracted(String cleanedContent, String proposalType, String proposalJson) {
        public static Extracted none(String content) {
            return new Extracted(content, null, null);
        }
    }

    /**
     * raw 응답 텍스트에서 첫 번째 액션 블록을 분리.
     *  - 매칭 없으면 원문 그대로 반환.
     *  - 매칭되면 본문에서 블록 제거 + proposalJson 에 type 포함시켜 저장.
     */
    public Extracted extract(String raw) {
        if (raw == null || raw.isBlank()) return Extracted.none(raw);
        Matcher m = BLOCK_RE.matcher(raw);
        if (!m.find()) return Extracted.none(raw);

        String type = m.group("type").toLowerCase();
        String body = m.group("body").trim();
        String cleaned = (raw.substring(0, m.start()) + raw.substring(m.end()))
            .replaceAll("\\n{3,}", "\n\n").trim();

        // body 자체가 유효 JSON 인지 검증 + type 필드 강제 주입
        try {
            JsonNode node = mapper.readTree(body);
            if (!node.isObject()) {
                return Extracted.none(raw);
            }
            ((com.fasterxml.jackson.databind.node.ObjectNode) node).put("type", type);
            return new Extracted(cleaned, type, mapper.writeValueAsString(node));
        } catch (Exception e) {
            return Extracted.none(raw);
        }
    }
}
