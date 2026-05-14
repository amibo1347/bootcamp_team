package com.team.intranet.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 일반 기안 본문 (formCode = "GENERIC").
 * 정형 양식이 아닌 자유 결재용 — VacationRequest 와 동일 패턴.
 */
@Entity
@Table(name = "tbl_generic_request")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class GenericRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "generic_request_id")
    private Long genericRequestId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "approval_id", nullable = false, unique = true)
    private Approval approval; // 어느 결재 문서의 본문인지 (1:1)

    @Lob
    @Column(name = "content", nullable = false, columnDefinition = "CLOB")
    private String content; // 기안 본문
}
