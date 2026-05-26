package com.team.intranet.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 동적 본문 한 필드 값 (B안). 회사 사본 양식의 결재 본문이 여기 (approval_id, field_key) 단위로 쌓인다.
 * 시스템 디폴트(VACATION/GENERIC/EXPENSE) 결재는 기존 fixed 본문 테이블에 저장되고 여기는 안 들어간다.
 *
 * field_key 는 FormTemplate.fieldSchema 의 key 와 매칭. multi-select 처럼 값이 여러 개인 경우는
 * 같은 field_key 로 여러 row 가 생성된다 (간단한 모델 — 별도 join 테이블 없이 다중값 표현).
 */
@Entity
@Table(name = "tbl_approval_field_value",
    indexes = @Index(name = "idx_afv_approval", columnList = "approval_id"))
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED) @AllArgsConstructor @Builder
public class ApprovalFieldValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "field_value_id")
    private Long fieldValueId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "approval_id", nullable = false)
    private Approval approval;

    @Column(name = "field_key", nullable = false, length = 100)
    private String fieldKey; // schema 의 필드 key

    @Lob
    @Column(name = "field_value", columnDefinition = "CLOB")
    private String fieldValue; // 모든 타입을 문자열로 보관 (number/date 도 ISO 문자열). 렌더 시 schema type 로 해석.
}
