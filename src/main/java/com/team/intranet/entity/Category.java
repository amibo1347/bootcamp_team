package com.team.intranet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import lombok.Setter;

import com.team.intranet.entity.Member;
import com.team.intranet.dto.CategoryDto;

@Entity
@Table(name = "tbl_category", uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "name"}))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Long categoryId; // 카테고리 id (PK)

    @Column(name = "name", nullable = false)
    private String name; // 카테고리명 (예: "회의", "운동", "휴가")

    @Column(name = "color", nullable = false)
    private String color; // 카테고리 색상 (HEX 코드, 예: "#FF5733")

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member owner;

}
