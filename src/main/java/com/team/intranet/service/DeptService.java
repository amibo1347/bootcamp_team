package com.team.intranet.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.team.intranet.repository.DeptRepository;
import com.team.intranet.entity.Dept;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeptService {
    private final DeptRepository deptRepository;

    public List<Dept> findAll(){
        return deptRepository.findAll();
    }
}
