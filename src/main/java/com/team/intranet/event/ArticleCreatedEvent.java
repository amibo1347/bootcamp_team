package com.team.intranet.event;

import java.util.List;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 게시글 작성이 끝난 직후 발행되는 도메인 이벤트.
 *
 * 알림 발송은 메인 트랜잭션 커밋 이후에만 수행되어야 한다.
 * (REQUIRES_NEW 로 같은 메서드 안에서 알림 INSERT 시 미커밋 parent FK 잠금 대기로 인한 hang 방지)
 *
 * 엔티티 참조 대신 id 만 담아 트랜잭션 경계를 안전하게 넘긴다.
 */
@Getter
@RequiredArgsConstructor
public class ArticleCreatedEvent {
    private final Long articleId;       // 작성된 게시글 id
    private final List<Long> recipientIds; // 알림을 받을 회원 id 목록 (작성자 자신은 제외하여 전달)
}
