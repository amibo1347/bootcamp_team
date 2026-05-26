package com.team.intranet.controller.api;

import org.springframework.http.HttpStatus;
  import org.springframework.http.ResponseEntity;
  import org.springframework.web.bind.annotation.RestController;
  import org.springframework.web.bind.annotation.GetMapping;
  import org.springframework.web.bind.annotation.ModelAttribute;
  import org.springframework.web.bind.annotation.PathVariable;
  import org.springframework.web.bind.annotation.PostMapping;
  import org.springframework.web.bind.annotation.RequestMapping;
    import org.springframework.web.bind.annotation.SessionAttribute;

  import com.team.intranet.dto.CalendarDto;
  import com.team.intranet.service.CalendarService;
  import com.team.intranet.session.MemberSession;

  import java.util.List;

  import lombok.RequiredArgsConstructor;

  @RestController
  @RequestMapping("/api/calendars")
  @RequiredArgsConstructor
public class CalendarApiController {
    
    private final CalendarService calendarService;

    @GetMapping("")
    public ResponseEntity<List<CalendarDto>> getCalendars(
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
            if( ms == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            return ResponseEntity.ok(calendarService.getCalendars(ms));
        }
    

     @GetMapping("/{calendarId}")
  public ResponseEntity<CalendarDto> getCalendar(
          @PathVariable Long calendarId,
          @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
      if (ms == null) {
          return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
      }
      return ResponseEntity.ok(calendarService.getCalendar(ms, calendarId));
  }

  @PostMapping("/new")
  public ResponseEntity<Void> createCalendar(
          @SessionAttribute(name = "memberSession", required = false) MemberSession ms,
          @ModelAttribute CalendarDto dto) {
      if (ms == null) {
          return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
      }
      calendarService.createCalendar(ms, dto);
      return ResponseEntity.ok().build();
  }

  @PostMapping("/{calendarId}/edit")
  public ResponseEntity<Void> updateCalendar(
          @PathVariable Long calendarId,
          @SessionAttribute(name = "memberSession", required = false) MemberSession ms,
          @ModelAttribute CalendarDto dto) {
      if (ms == null) {
          return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
      }
      calendarService.updateCalendar(ms, calendarId, dto);
      return ResponseEntity.ok().build();
  }

  @PostMapping("/{calendarId}/delete")
  public ResponseEntity<Void> deleteCalendar(
          @PathVariable Long calendarId,
          @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
      if (ms == null) {
          return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
      }
      calendarService.deleteCalendar(ms, calendarId);
      return ResponseEntity.noContent().build();
  }
}
