package com.team.intranet.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.team.intranet.entity.FormTemplate;
import java.util.List;
import java.util.Optional;
public interface FormTemplateRepository extends JpaRepository<FormTemplate, Long>{
    
    Optional<FormTemplate> findByFormCode(String formCode);
    List<FormTemplate> findAllByIsActiveTrue();
    boolean existsByFormCode(String formCode);
}
