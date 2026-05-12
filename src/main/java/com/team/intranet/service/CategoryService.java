package com.team.intranet.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import java.util.List;

import com.team.intranet.repository.*;
import com.team.intranet.entity.*;
import com.team.intranet.enums.ErrorCode;
import com.team.intranet.exception.BusinessException;
import com.team.intranet.dto.*;
import com.team.intranet.session.MemberSession;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CalendarRepository calendarRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public Category createCategory(MemberSession ms, CategoryDto dto){

        Member owner = memberRepository.findById(ms.getMemberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if(categoryRepository.existsByOwnerAndName(owner, dto.getName())){
            throw new BusinessException(ErrorCode.DUPLICATE_CATEGORY_NAME);
        }
        Category category = Category.builder()
                .name(dto.getName())
                .color(dto.getColor())
                .owner(owner)
                .build();
        return categoryRepository.save(category);
    }

    @Transactional
    public Category updateCategory(MemberSession ms, Long categoryId, CategoryDto dto){
        
        Member owner = memberRepository.findById(ms.getMemberId())
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        
        Category category = categoryRepository
            .findByCategoryIdAndOwner(categoryId, owner)
            .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        
        if(!category.getName().equals(dto.getName())
            && categoryRepository.existsByOwnerAndName(owner, dto.getName())){
            throw new BusinessException(ErrorCode.DUPLICATE_CATEGORY_NAME);
        }

        category.setName(dto.getName());
        category.setColor(dto.getColor());
        
        return category;
    }

    @Transactional
    public void deleteCategory(MemberSession ms, Long categoryId){

        Member owner = memberRepository.findById(ms.getMemberId())
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        Category category = categoryRepository
            .findByCategoryIdAndOwner(categoryId, owner)
            .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        categoryRepository.delete(category);
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> getMyCategories(MemberSession ms){

        Member owner = memberRepository.findById(ms.getMemberId())
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        
        List<CategoryDto> categories = categoryRepository.findByOwner(owner)
            .stream().map(CategoryDto :: from).toList();
        
        return categories;
    }
}
