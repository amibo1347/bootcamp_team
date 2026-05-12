package com.team.intranet.controller.api;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.http.HttpStatus;

import lombok.RequiredArgsConstructor;

import com.team.intranet.service.CategoryService;
import com.team.intranet.session.MemberSession;
import com.team.intranet.dto.CategoryDto;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.SessionAttribute;

import org.springframework.web.bind.annotation.PathVariable;
import java.util.List;


@Controller
uestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryApiController {
    
    private final CategoryService categoryService;
Mapping("")
ponseBody
        es ponseEntity< List<CategoryDto>> getMyCategories
            nAttribute(name = "memberSession", required = false) MemberSession ms) {
        
        if(ms == null){
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            return ResponseEntity.ok(categoryService.getMyCategories(ms));
      }
    
            apping("/new")
            eBody
        ic ResponseEntity<Void> createCategory(
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms,
            @RequestBody CategoryDto dto) {
        if (ms == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
      categoryService.createCategory(ms, dto);
      return ResponseEntity.ok().build();
    }
    
            apping("/{categoryId}/edit")
            eBody
            esponseEntity<Void> updateCategory(
            @PathVariable Long categoryId,
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms,
            @RequestBody CategoryDto dto) {
            if (ms == null) { 
        
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
      categoryService.updateCategory(ms, categoryId, dto);
        return ResponseEntity.ok().build();
    }
    
            ping("/{categoryId}/delete")
            eBody
        ic ResponseEntity<Void> deleteCategory(
            @PathVariable Long categoryId,
            @SessionAttribute(name = "memberSession", required = false) MemberSession ms) {
        if (ms == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
  categoryService.deleteCategory(ms, categoryId);
      return ResponseEntity.noContent().build();
  }
    
}
