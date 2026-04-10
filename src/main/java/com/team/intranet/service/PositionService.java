package com.team.intranet.service;

import java.util.List;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import com.team.intranet.entity.Position;
import com.team.intranet.repository.PositionRepository;

@Service
@RequiredArgsConstructor
public class PositionService {
    
    private final PositionRepository positionRepository;

    public List<Position> findAll() {
    return positionRepository.findAll();
}
}
