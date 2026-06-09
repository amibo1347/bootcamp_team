package com.team.intranet.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team.intranet.entity.ArticleImage;

public interface ArticleImageRepository extends JpaRepository<ArticleImage, Long>{
    
}
