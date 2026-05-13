package com.team.intranet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_form_template",
        uniqueConstraints = @UniqueConstraint(columnNames = "form_code")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FormTemplate {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "form_template_id")
    private Long formTemplateId;    // 양식 id

    @Column(name = "form_code", nullable = false, unique = true, length = 50)
    private String formCode;    // 양식 식별 코드

    @Column(name = "name", nullable = false, length = 100)
    private String name;    // 양식명

    @Column(name = "content", length = 500)
    private String content; // 양식 설명

    @Column(name = "is_active", nullable = false)
    private boolean isActive;   // 노출 여부(false면 선택에서 숨김)
}
