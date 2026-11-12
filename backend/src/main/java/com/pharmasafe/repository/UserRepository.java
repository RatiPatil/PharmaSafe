package com.pharmasafe.repository;

import com.pharmasafe.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<User> {

    Optional<User> findByUsername(String username);
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByEmployeeIdIgnoreCase(String employeeId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> lockById(@Param("id") Long id);

    boolean existsByUsername(String username);

    boolean existsByRole(String role);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.username = :username")
    Optional<User> findByUsernameForUpdate(@Param("username") String username);
    long countByEnabledTrue();
    @org.springframework.data.jpa.repository.Query("SELECT u.username, COUNT(c) FROM SafetyCase c LEFT JOIN c.assignedToUser u WHERE c.workflowState NOT IN ('Closed', 'Submitted') GROUP BY u.username ORDER BY COUNT(c) DESC")
    java.util.List<Object[]> caseWorkload();
    org.springframework.data.domain.Page<User> findByUsernameContainingIgnoreCaseOrFullNameContainingIgnoreCase(
            String username, String fullName, org.springframework.data.domain.Pageable pageable);
}
