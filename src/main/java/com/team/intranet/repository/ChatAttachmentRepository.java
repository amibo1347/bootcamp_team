package com.team.intranet.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team.intranet.entity.ChatAttachment;

public interface ChatAttachmentRepository extends JpaRepository<ChatAttachment, Long> {

    List<ChatAttachment> findByMessage_MessageId(Long messageId);
}
