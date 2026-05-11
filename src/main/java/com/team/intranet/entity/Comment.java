package com.team.intranet.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;
import java.time.LocalDateTime;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name="tbl_comment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor 
@Builder
public class Comment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "comment_id")
    private Long commentId;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "article_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Article article;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = true)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Member author;
    
    @Column(name="author_display_name")
    private String authorDisplayName;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Comment parent;

    @Lob
    @Column(name = "content", columnDefinition = "CLOB", nullable = false)
    private String content;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public static Comment create(Article article, Member author, Comment parent, String content){
        return Comment.builder()
            .article(article)
            .author(author)
            .parent(parent)
            .content(content)
            .createdAt(LocalDateTime.now())
            .build();
    }

    public void updateContent(String content){
        this.content = content;
    }

    public boolean isAuthor(Long memberId){
        return this.author != null && this.author.getMemberId().equals(memberId);
    }

}
