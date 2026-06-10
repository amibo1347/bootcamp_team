package com.team.intranet.service.ai;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import com.team.intranet.enums.ErrorCode;
import com.team.intranet.exception.BusinessException;

/**
 * 업로드 문서에서 평문 텍스트를 추출한다 (AI 요약용).
 *  - 지원: PDF(PDFBox), PPTX·DOCX(Apache POI), TXT/MD/CSV(평문).
 *  - 스캔 이미지 PDF처럼 텍스트가 없으면 FILE_TEXT_EMPTY.
 *  - 토큰 절약을 위해 MAX_CHARS 로 잘라서 반환.
 */
@Component
public class DocumentTextExtractor {

    /** LLM 프롬프트 토큰 절약 — 이 길이까지만 사용. */
    private static final int MAX_CHARS = 30000;

    public String extract(String fileName, byte[] data) {
        if (data == null || data.length == 0) {
            throw new BusinessException(ErrorCode.FILE_TEXT_EMPTY);
        }
        String name = fileName == null ? "" : fileName.toLowerCase();

        String text;
        try {
            if (name.endsWith(".pdf")) {
                text = extractPdf(data);
            } else if (name.endsWith(".pptx")) {
                text = extractPptx(data);
            } else if (name.endsWith(".docx")) {
                text = extractDocx(data);
            } else if (name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".csv")) {
                text = new String(data, StandardCharsets.UTF_8);
            } else {
                throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE);
            }
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            // 손상 파일·파싱 실패 등
            throw new BusinessException(ErrorCode.FILE_TEXT_EMPTY);
        }

        if (text == null || text.isBlank()) {
            throw new BusinessException(ErrorCode.FILE_TEXT_EMPTY);
        }
        text = text.strip();
        return text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text;
    }

    private String extractPdf(byte[] data) throws Exception {
        try (PDDocument doc = PDDocument.load(data)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    private String extractPptx(byte[] data) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (XMLSlideShow ppt = new XMLSlideShow(new ByteArrayInputStream(data))) {
            int slideNo = 1;
            for (XSLFSlide slide : ppt.getSlides()) {
                sb.append("[슬라이드 ").append(slideNo++).append("]\n");
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape ts) {
                        String t = ts.getText();
                        if (t != null && !t.isBlank()) sb.append(t).append('\n');
                    }
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private String extractDocx(byte[] data) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(data));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        }
    }
}
