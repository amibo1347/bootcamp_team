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

    public static CalendarDto from(Calendar calendar){
        CalendarDto dto = new CalendarDto();
        dto.setCalendarId(calendar.getCalendarId());
        dto.setTitle(calendar.getTitle());
        dto.setDescription(calendar.getDescription());
        dto.setStartAt(calendar.getStartAt());
        dto.setEndAt(calendar.getEndAt());
        dto.setAllDay(calendar.isAllDay());
        dto.setRepeat(calendar.isRepeat());
        dto.setRepeatType(calendar.getRepeatType());
        dto.setRepeatEndAt(calendar.getRepeatEndAt());
        dto.setAlert(calendar.isAlert());
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
