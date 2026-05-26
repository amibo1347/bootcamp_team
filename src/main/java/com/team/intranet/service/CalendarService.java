package com.team.intranet.service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team.intranet.repository.CalendarRepository;
import com.team.intranet.repository.CalendarShareDeptRepository;
import com.team.intranet.repository.CalendarShareMemberRepository;
import com.team.intranet.repository.CategoryRepository;
import com.team.intranet.repository.DeptRepository;
import com.team.intranet.repository.MemberRepository;
import com.team.intranet.session.MemberSession;
import com.team.intranet.dto.CalendarDto;
import com.team.intranet.entity.Calendar;
import com.team.intranet.entity.Category;
import com.team.intranet.entity.Member;
import com.team.intranet.entity.Dept;
import com.team.intranet.enums.Visibility;
import com.team.intranet.entity.CalendarShareDept;
import com.team.intranet.entity.CalendarShareMember;
import com.team.intranet.exception.BusinessException;
import com.team.intranet.enums.ErrorCode;

import java.util.List;

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


    private void saveDeptShares(Calendar calendar, List<Long> deptIds, Member owner){
        if (deptIds == null || deptIds.isEmpty()) return;
        for (Long deptId : deptIds) {
            Dept dept = deptRepository.findById(deptId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPT_NOT_FOUND));

            if (!dept.getCompany().getCompanyId().equals(owner.getCompany().getCompanyId())){
                throw new BusinessException(ErrorCode.COMPANY_ACCESS_DENIED);
            }

            shareDeptRepository.save(CalendarShareDept.builder()
                    .calendar(calendar)
                    .dept(dept)
                    .build()
                    );
        }
    }

    private void saveMemberShares(Calendar calendar, List<Long> memberIds, Member owner){
        if (memberIds == null || memberIds.isEmpty()) return;
        for (Long memberId : memberIds) {
            Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

            if (!member.getCompany().getCompanyId().equals(owner.getCompany().getCompanyId())){
                throw new BusinessException(ErrorCode.COMPANY_ACCESS_DENIED);
            }

            shareMemberRepository.save(CalendarShareMember.builder()
                    .calendar(calendar)
                    .member(member)
                    .build()
                    );
        }
    }
    @Transactional
    public Calendar createCalendar(MemberSession ms, CalendarDto dto) {
        Member member = memberRepository.findById(ms.getMemberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        Category category = null;
        if (dto.getCategoryId() != null) {
            category = categoryRepository
                    .findByCategoryIdAndOwner(dto.getCategoryId(), member)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        }

        Calendar calendar = Calendar.builder()
                .member(member)
                .title(dto.getTitle())
                .description(dto.getDescription())
                .startAt(dto.getStartAt())
                .endAt(dto.getEndAt())
                .allDay(dto.isAllDay())
                .isRepeat(dto.isRepeat())
                .repeatType(dto.getRepeatType())
                .repeatEndAt(dto.getRepeatEndAt())
                .repeatWeekdays(dto.getRepeatWeekdays())
                .repeatMonthDays(dto.getRepeatMonthDays())
                .isAlert(dto.isAlert())
                .alertMinutesBefore(dto.getAlertMinutesBefore())
                .location(dto.getLocation())
                .visibility(dto.getVisibility())
                .category(category)
                .createdAt(LocalDateTime.now())
                .build();
        Calendar saved = calendarRepository.save(calendar);

        switch (dto.getVisibility()){
            case DEPARTMENT -> saveDeptShares(saved, dto.getShareDeptIds(), member);
            case SPECIFIC -> saveMemberShares(saved, dto.getShareMemberIds(), member);
            default -> {}
        }

        return saved;
    }

    @Transactional
    public CalendarDto getCalendar(MemberSession ms, Long calendarId){
        Calendar calendar = calendarRepository.findById(calendarId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CALENDAR_NOT_FOUND));
        
        assertAccessible(calendar, ms);

        CalendarDto dto = CalendarDto.from(calendar);

        dto.setShareDeptIds(
            shareDeptRepository.findAllByCalendar(calendar).stream()
                .map(s -> s.getDept().getDeptId()).toList());
        dto.setShareMemberIds(
            shareMemberRepository.findAllByCalendar(calendar).stream()
                .map(s -> s.getMember().getMemberId()).toList());
        
        return dto;
    }

    @Transactional
    public Calendar updateCalendar(MemberSession ms, Long calendarId, CalendarDto dto){

        Calendar calendar = calendarRepository.findById(calendarId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CALENDAR_NOT_FOUND));
        assertOwner(calendar, ms);

        Member owner = calendar.getMember();
        Category category = null;
        if (dto.getCategoryId() != null) {
            category = categoryRepository
                .findByCategoryIdAndOwner(dto.getCategoryId(), owner)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        }

        calendar.setTitle(dto.getTitle());
        calendar.setDescription(dto.getDescription());
        calendar.setStartAt(dto.getStartAt());
        calendar.setEndAt(dto.getEndAt());
        calendar.setAllDay(dto.isAllDay());
        calendar.setRepeat(dto.isRepeat());
        calendar.setRepeatType(dto.getRepeatType());
        calendar.setRepeatEndAt(dto.getRepeatEndAt());
        calendar.setRepeatWeekdays(dto.getRepeatWeekdays());
        calendar.setRepeatMonthDays(dto.getRepeatMonthDays());
        calendar.setAlert(dto.isAlert());
        calendar.setAlertMinutesBefore(dto.getAlertMinutesBefore());
        calendar.setLocation(dto.getLocation());
        calendar.setVisibility(dto.getVisibility());
        calendar.setCategory(category);

        shareDeptRepository.deleteAllByCalendar(calendar);
        shareMemberRepository.deleteAllByCalendar(calendar);

        switch (dto.getVisibility()) {
            case DEPARTMENT -> saveDeptShares(calendar, dto.getShareDeptIds(), owner);
            case SPECIFIC   -> saveMemberShares(calendar, dto.getShareMemberIds(), owner);
            default -> {}
        }
        return calendar;
    }

    @Transactional
    public void deleteCalendar(MemberSession ms, Long calendarId){
        Calendar calendar = calendarRepository.findById(calendarId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CALENDAR_NOT_FOUND));
        assertOwner(calendar, ms);

        shareDeptRepository.deleteAllByCalendar(calendar);
        shareMemberRepository.deleteAllByCalendar(calendar);
        calendarRepository.delete(calendar);
    }

    @Transactional(readOnly = true)
    public List<CalendarDto> getCalendars(MemberSession ms){
        Member me = memberRepository.findById(ms.getMemberId())
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        Dept myDept = me.getDept();

        List<Calendar> calendars = calendarRepository.findAccessibleByMember(me, myDept);
        if (calendars.isEmpty()) return List.of();

        // N+1 회피: 한 번의 IN 쿼리로 모든 share 를 끌어와 calendarId 별로 그룹핑.
        // (CalendarShare* 의 Calendar 는 LAZY proxy 라 getCalendarId() 호출은 추가 쿼리 없음)
        Map<Long, List<Long>> deptIdsByCalendar = shareDeptRepository.findAllByCalendarIn(calendars).stream()
            .collect(Collectors.groupingBy(
                s -> s.getCalendar().getCalendarId(),
                Collectors.mapping(s -> s.getDept().getDeptId(), Collectors.toList())
            ));
        Map<Long, List<Long>> memberIdsByCalendar = shareMemberRepository.findAllByCalendarIn(calendars).stream()
            .collect(Collectors.groupingBy(
                s -> s.getCalendar().getCalendarId(),
                Collectors.mapping(s -> s.getMember().getMemberId(), Collectors.toList())
            ));

        return calendars.stream()
                .map(c -> {
                    CalendarDto dto = CalendarDto.from(c);
                    dto.setShareDeptIds(deptIdsByCalendar.getOrDefault(c.getCalendarId(), Collections.emptyList()));
                    dto.setShareMemberIds(memberIdsByCalendar.getOrDefault(c.getCalendarId(), Collections.emptyList()));
                    return dto;
                })
                .toList();
    }

    private void assertAccessible(Calendar calendar, MemberSession ms){
        if (calendar.getMember().getMemberId().equals(ms.getMemberId())) return;

        if (calendar.getVisibility() == Visibility.COMPANY
            && calendar.getMember().getCompany().getCompanyId().equals(ms.getCompanyId())) return;

        // DEPARTMENT / SPECIFIC 검증은 Member 가 필요 → 한 번만 lazy 조회.
        if (calendar.getVisibility() == Visibility.DEPARTMENT
                || calendar.getVisibility() == Visibility.SPECIFIC) {
            Member me = memberRepository.findById(ms.getMemberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

            if (calendar.getVisibility() == Visibility.DEPARTMENT
                    && me.getDept() != null
                    && shareDeptRepository.existsByCalendarAndDept(calendar, me.getDept())) return;

            if (calendar.getVisibility() == Visibility.SPECIFIC
                    && shareMemberRepository.existsByCalendarAndMember(calendar, me)) return;
        }

        throw new BusinessException(ErrorCode.CALENDAR_ACCESS_DENIED);
     }

     private void assertOwner(Calendar calendar, MemberSession ms){
        if (!calendar.getMember().getMemberId().equals(ms.getMemberId())){
            throw new BusinessException(ErrorCode.CALENDAR_NOT_OWNER);
        }
     }
}
