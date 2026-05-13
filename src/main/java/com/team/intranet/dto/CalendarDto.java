package com.team.intranet.dto;

import com.team.intranet.enums.RepeatType;
import com.team.intranet.enums.Visibility;
import com.team.intranet.entity.Calendar;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CalendarDto {
    
    private Long calendarId;
    private String title;
    private String description;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private boolean allDay;
    private boolean isRepeat;
    private RepeatType repeatType;
    private LocalDateTime repeatEndAt;
    private Integer repeatWeekdays;     // 매주: 요일 비트마스크 (MON=1, TUE=2, ..., SUN=64). null/0 이면 시작일 요일 단일.
    private Integer repeatMonthDays;    // 매월: 일자 비트마스크 (1일=1<<0 .. 31일=1<<30). null/0 이면 시작일 일자 단일.
    private boolean isAlert;
    private Integer alertMinutesBefore;
    private String location;
    private Visibility visibility;
    private Long categoryId;
    private String categoryName;
    private String categoryColor;
    private Long memberId;
    private String memberName;
    private List<Long> shareDeptIds;
    private List<Long> shareMemberIds;

    /*
     * Spring @ModelAttribute 폼 바인딩 별칭 setter.
     * Lombok @Data 는 `boolean isRepeat/isAlert/allDay` 필드에 대해 setter 를 `setRepeat/setAlert/setAllDay`
     * 로만 생성한다 → form key 가 `isRepeat=true` 처럼 들어오면 매핑이 실패해 항상 false 로 저장되는 버그가 있었다.
     * 프론트가 보내는 키(`isRepeat`, `isAlert`, `allDay`)에 대응하도록 setIs- 형태 setter 를 명시한다.
     */
    public void setIsRepeat(boolean value) { this.isRepeat = value; }
    public void setIsAlert(boolean value)  { this.isAlert = value; }
    public void setIsAllDay(boolean value) { this.allDay = value; }

    public static CalendarDto from(Calendar calendar){
        CalendarDto dto = new CalendarDto();
        dto.setCalendarId(calendar.getCalendarId());
        dto.setTitle(calendar.getTitle());
        dto.setDescription(calendar.getDescription());
        dto.setStartAt(calendar.getStartAt());
        dto.setEndAt(calendar.getEndAt());
        dto.setAllDay(calendar.isAllDay());
        dto.setIsRepeat(calendar.isRepeat());
        dto.setRepeatType(calendar.getRepeatType());
        dto.setRepeatEndAt(calendar.getRepeatEndAt());
        dto.setRepeatWeekdays(calendar.getRepeatWeekdays());
        dto.setRepeatMonthDays(calendar.getRepeatMonthDays());
        dto.setIsAlert(calendar.isAlert());
        dto.setAlertMinutesBefore(calendar.getAlertMinutesBefore());
        dto.setLocation(calendar.getLocation());
        dto.setVisibility(calendar.getVisibility());
        if (calendar.getCategory() != null) {
            dto.setCategoryId(calendar.getCategory().getCategoryId());
            dto.setCategoryName(calendar.getCategory().getName());
            dto.setCategoryColor(calendar.getCategory().getColor());
        }
        if (calendar.getMember() != null) {
            dto.setMemberId(calendar.getMember().getMemberId());
            dto.setMemberName(calendar.getMember().getName());
        }
        return dto;
    }
}
