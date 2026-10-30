package com.pharmasafe.repository;

import com.pharmasafe.entity.MeddraTerm;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MeddraTermRepository extends JpaRepository<MeddraTerm, Long> {

    Optional<MeddraTerm> findByCode(String code);

    @Query("SELECT m FROM MeddraTerm m WHERE " +
           "(:query IS NULL OR :query = '' OR LOWER(m.term) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(m.code) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(m.llt) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "AND (:soc IS NULL OR :soc = '' OR LOWER(m.soc) = LOWER(:soc)) " +
           "AND (:level IS NULL OR :level = '' OR LOWER(m.level) = LOWER(:level))")
    Page<MeddraTerm> searchTerms(
            @Param("query") String query,
            @Param("soc") String soc,
            @Param("level") String level,
            Pageable pageable
    );

    @Query("SELECT DISTINCT m.soc FROM MeddraTerm m ORDER BY m.soc")
    List<String> findDistinctSocs();
}
