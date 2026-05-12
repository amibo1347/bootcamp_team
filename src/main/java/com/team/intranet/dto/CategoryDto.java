package com.team.intranet.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.team.intranet.entity.Category;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryDto {
    
    private Long categoryId;
    private String name;
    private String color;
    private Long ownerId;

    public static CategoryDto from(Category category){
        CategoryDto dto = new CategoryDto();
        dto.setCategoryId(category.getCategoryId());
        dto.setName(category.getName());
        dto.setColor(category.getColor());
        if (category.getOwner() != null) {
            dto.setOwnerId(category.getOwner().getMemberId());
        }
        return dto;
    }
}
