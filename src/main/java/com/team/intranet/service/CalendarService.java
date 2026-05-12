package com.team.intranet.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team.intranet.repository.CalendarRepository;
import com.team.intranet.repository.CalendarShareDeptRepository;
import com.team.intranet.repository.CalendarShareMemberRepository;
import com.team.intranet.repository.CategoryRepository;
import com.team.intranet.repository.DeptRepository;
import com.team.intranet.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CalendarService {
    
    private final CalendarRepository calendarRepository;
    private final CategoryRepository categoryRepository;
    private final CalendarShareMemberRepository shareMemberRepository;
    private final CalendarShareDeptRepository shareDeptRepository;
    private final MemberRepository memberRepository;
    private final DeptRepository deptRepository;


}
