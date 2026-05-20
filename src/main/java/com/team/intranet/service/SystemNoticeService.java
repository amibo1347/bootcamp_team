package com.team.intranet.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team.intranet.entity.SystemNotice;
import com.team.intranet.enums.SystemNoticeType;
import com.team.intranet.repository.SystemNoticeRepository;

import lombok.RequiredArgsConstructor;

/**
 * 시스템 공지 관리 (MASTER) + 현재 노출 공지 조회 (전 회사 배너).
 */
@Service
@RequiredArgsConstructor
public class SystemNoticeService {

    private final SystemNoticeRepository systemNoticeRepository;

    /** 전체 공지 목록 (최신 등록 순). */
    @Transactional(readOnly = true)
    public List<SystemNotice> list() {
        return systemNoticeRepository.findAll(Sort.by(Sort.Direction.DESC, "noticeId"));
    }

    /** 현재 노출 대상 공지. 여러 건이면 가장 최근 시작 건. 없으면 null. */
    @Transactional(readOnly = true)
    public SystemNotice findActiveNotice() {
        List<SystemNotice> active = systemNoticeRepository.findActive(LocalDateTime.now());
        return active.isEmpty() ? null : active.get(0);
    }

    /** 공지 등록. startsAt 이 null 이면 즉시 시작. */
    @Transactional
    public void create(String content, SystemNoticeType noticeType,
                       LocalDateTime startsAt, LocalDateTime endsAt) {
        systemNoticeRepository.save(SystemNotice.create(
                content.trim(),
                noticeType,
                startsAt != null ? startsAt : LocalDateTime.now(),
                endsAt));
    }

    @Transactional
    public void delete(Long noticeId) {
        systemNoticeRepository.deleteById(noticeId);
    }
}
