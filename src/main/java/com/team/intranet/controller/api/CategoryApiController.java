package com.team.intranet.controller.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team.intranet.config.AuthenticatedMember;
import com.team.intranet.dto.CategoryDto;
import com.team.intranet.service.CategoryService;
import com.team.intranet.session.MemberSession;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryApiController {

    private final CategoryService categoryService;

    @GetMapping("")
    public ResponseEntity<List<CategoryDto>> getMyCategories(@AuthenticatedMember MemberSession ms) {
        return ResponseEntity.ok(categoryService.getMyCategories(ms));
    }

    @PostMapping("/new")
    public ResponseEntity<Void> createCategory(@AuthenticatedMember MemberSession ms,
                                               @RequestBody CategoryDto dto) {
        categoryService.createCategory(ms, dto);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{categoryId}/edit")
    public ResponseEntity<Void> updateCategory(@PathVariable Long categoryId,
                                               @AuthenticatedMember MemberSession ms,
                                               @RequestBody CategoryDto dto) {
        categoryService.updateCategory(ms, categoryId, dto);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{categoryId}/delete")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long categoryId,
                                               @AuthenticatedMember MemberSession ms) {
        categoryService.deleteCategory(ms, categoryId);
        return ResponseEntity.noContent().build();
    }
}
