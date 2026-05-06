package com.team.intranet.repository;

import com.team.intranet.entity.Board;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BoardRepository extends JpaRepository<Board, Long> {
    List<Board> findAllByCompany_CompanyId(Long companyId);
}
