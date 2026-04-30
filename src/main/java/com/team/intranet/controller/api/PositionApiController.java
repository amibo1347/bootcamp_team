package com.team.intranet.controller.api;

import com.team.intranet.dto.PositionDto;
import com.team.intranet.service.PositionService;
import com.team.intranet.session.MemberSession;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/api/admin/position")
@RequiredArgsConstructor
public class PositionApiController {

    private final PositionService positionService;

    // 직급 생성 처리
    @PostMapping("/create")
    @ResponseBody // JSON 또는 텍스트 응답을 위해 추가
    public ResponseEntity<?> createPosition(@SessionAttribute(name = "memberSession", required = false) MemberSession ms,
                                 @RequestBody PositionDto positionDto) {
        try {
            positionService.createPosition(ms, positionDto);
            return ResponseEntity.ok("직급이 성공적으로 생성되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("직급 생성 실패: " + e.getMessage());
        }
    }

    // 직급 수정 처리
    @PostMapping("/update/{positionId}")
    @ResponseBody
    public ResponseEntity<?> updatePosition(@SessionAttribute(name = "memberSession", required = false) MemberSession ms,
                                 @PathVariable Long positionId,
                                 @RequestBody PositionDto positionDto) {
        try {
            positionService.updatePosition(ms, positionDto, positionId);
            return ResponseEntity.ok("직급 정보가 수정되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("직급 수정 실패: " + e.getMessage());
        }
    }

    // 직급 삭제 처리
    @PostMapping("/delete/{positionId}")
    @ResponseBody
    public ResponseEntity<?> deletePosition(@SessionAttribute(name = "memberSession", required = false) MemberSession ms,
                                 @PathVariable Long positionId) {
        try {
            positionService.deletePosition(ms, positionId);
            return ResponseEntity.ok("직급이 삭제되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("직급 삭제 실패: " + e.getMessage());
        }
    }
}
