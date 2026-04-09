package com.team.intranet.entity;

import com.team.intranet.entity.Company;
import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name="tbl_dept")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Dept {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="dept_id")
    private Long deptId;

    @Column(name="dept_name")
    private String deptName;

    @Column(name="dept_code")
    private String deptCode;

    @ManyToOne
    @JoinColumn(name="company_id")
    private Company company;
}
