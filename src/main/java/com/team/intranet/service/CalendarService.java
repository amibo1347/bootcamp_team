package com.team.intranet.service;

import java.time.LocalDateTime;

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

        // 일정 셀에 공유자 프로필 동그라미·부서명 배지를 그리려면 목록 조회에도 shareIds 가 필요.
        // 단건 조회(getCalendar) 와 동일하게 dept/member share 를 추가로 채워 응답한다.
        return calendarRepository.findAccessibleByMember(me, myDept).stream()
                .map(c -> {
                    CalendarDto dto = CalendarDto.from(c);
                    dto.setShareDeptIds(
                        shareDeptRepository.findAllByCalendar(c).stream()
                            .map(s -> s.getDept().getDeptId()).toList());
                    dto.setShareMemberIds(
                        shareMemberRepository.findAllByCalendar(c).stream()
                            .map(s -> s.getMember().getMemberId()).toList());
                    return dto;
                })
                .toList();
    }

    private void assertAccessible(Calendar calendar, MemberSession ms){
        if (calendar.getMember().getMemberId().equals(ms.getMemberId())) return;

        if (calendar.getVisibility() == Visibility.COMPANY
            && calendar.getMember().getCompany().getCompanyId().equals(ms.getCompanyId())) return;

        if (calendar.getVisibility() == Visibility.DEPARTMENT) {
            Member me = memberRepository.findById(ms.getMemberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
            if (me.getDept() != null
                && shareDeptRepository.existsByCalendarAndDept(calendar, me.getDept())) return;
        }

        if (calendar.getVisibility() == Visibility.SPECIFIC) {
            Member me = memberRepository.findById(ms.getMemberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
            if (shareMemberRepository.existsByCalendarAndMember(calendar, me)) return;
        }

      throw new BusinessException(ErrorCode.CALENDAR_ACCESS_DENIED);   
     }

     private void assertOwner(Calendar calendar, MemberSession ms){
        if (!calendar.getMember().getMemberId().equals(ms.getMemberId())){
            throw new BusinessException(ErrorCode.CALENDAR_NOT_OWNER);
        }
     }
}
