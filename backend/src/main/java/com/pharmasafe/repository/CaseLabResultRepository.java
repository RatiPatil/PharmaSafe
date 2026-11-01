package com.pharmasafe.repository;
import com.pharmasafe.entity.CaseLabResult;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface CaseLabResultRepository extends JpaRepository<CaseLabResult,Long> {
 List<CaseLabResult> findBySafetyCaseIdOrderByTestDateDescIdDesc(Long id);
 long countBySafetyCaseId(Long id);
}
