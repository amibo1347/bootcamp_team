package com.team.intranet.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import com.team.intranet.enums.VacationType;

@Entity
@Table(name = "tbl_vacation_request")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED) @AllArgsConstructor @Builder
public class VacationRequest {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vacation_request_id")
    private Long vacationRequestId;

     @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "approval_id", nullable = false, unique = true)
    private Approval approval; // 어느 결재 문서의 본문인지 (1:1)

    @Enumerated(EnumType.STRING)
    @Column(name = "vacation_type", nullable = false, length = 20)
     private VacationType vacationType; // 휴가 종류

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate; // 휴가 시작일

    @Column(name = "end_date", nullable = false)
     private LocalDate endDate; // 휴가 종료일

     @Column(name = "total_days", nullable = false)
     private Double totalDays; // 총 휴가일수 (반차 0.5 단위)

    @Lob
    @Column(name = "reason", columnDefinition = "CLOB")
    private String reason; // 휴가 사유
}
