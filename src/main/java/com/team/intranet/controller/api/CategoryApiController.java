package com.team.intranet.controller.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.SessionAttribute;

import org.springframework.web.bind.annotation.PathVariable;
import java.util.List;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryApiController {

    private final CategoryService categoryService;

    @GetMapping("")
    @ResponseBody
    public ResponseEntity<List<CategoryDto>> getMyCategories(
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {

        if (ms == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(categoryService.getMyCategories(ms));
    }

    @PostMapping("/new")
    @ResponseBody
    public ResponseEntity<Void> createCategory(
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms,
            @RequestBody CategoryDto dto) {
        if (ms == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        categoryService.createCategory(ms, dto);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{categoryId}/edit")
    @ResponseBody
    public ResponseEntity<Void> updateCategory(
            @PathVariable Long categoryId,
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms,
            @RequestBody CategoryDto dto) {
        if (ms == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        categoryService.updateCategory(ms, categoryId, dto);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{categoryId}/delete")
    @ResponseBody
    public ResponseEntity<Void> deleteCategory(
            @PathVariable Long categoryId,
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
  categoryService.deleteCategory(ms, categoryId);
      return ResponseEntity.noContent().build();
  }
    
}
