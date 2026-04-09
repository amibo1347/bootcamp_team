package com.team.intranet.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.team.intranet.entity.Position;

public interface  PositionRepository extends JpaRepository<Position, Long> {
    
}
