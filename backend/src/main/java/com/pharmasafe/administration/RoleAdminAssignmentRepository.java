package com.pharmasafe.administration;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RoleAdminAssignmentRepository extends JpaRepository<RoleAdminAssignment, String> {
  @Query(
      "select a from RoleAdminAssignment a left join fetch a.administrator left join fetch"
          + " a.assignedBy order by a.applicationRole")
  java.util.List<RoleAdminAssignment> findAllWithAdministrators();

  Optional<RoleAdminAssignment> findByAdministratorId(Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from RoleAdminAssignment a where a.applicationRole=:role")
  Optional<RoleAdminAssignment> lockRole(@Param("role") String role);
}
