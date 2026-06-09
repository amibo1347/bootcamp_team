package com.team.intranet.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

import  com.team.intranet.entity.Member;
import  com.team.intranet.entity.Category;

public interface  CategoryRepository extends JpaRepository<Category, Long>{

    List<Category> findByOwner(Member owner);
    boolean existsByOwnerAndName(Member owner, String name);
    Optional<Category> findByCategoryIdAndOwner(Long categoryId, Member owner);
}
