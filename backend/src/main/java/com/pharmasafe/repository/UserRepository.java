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
    @Query("SELECT s.user.username FROM EduStudent s WHERE UPPER(s.studentCode) = UPPER(:studentCode) AND s.user.role = 'ROLE_STUDENT'")
    Optional<String> findStudentLoginName(@Param("studentCode") String studentCode);
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
    long countByRole(String role);
    long countByAccountStatus(String status);
    long countByLockedTrue();
    @Query("SELECT u.role, COUNT(u) FROM User u GROUP BY u.role ORDER BY u.role")
    java.util.List<Object[]> roleDistribution();
    @org.springframework.data.jpa.repository.Query("SELECT u.username, COUNT(c) FROM SafetyCase c LEFT JOIN c.assignedToUser u WHERE c.workflowState NOT IN ('Closed', 'Submitted') GROUP BY u.username ORDER BY COUNT(c) DESC")
    java.util.List<Object[]> caseWorkload();
    org.springframework.data.domain.Page<User> findByUsernameContainingIgnoreCaseOrFullNameContainingIgnoreCase(
            String username, String fullName, org.springframework.data.domain.Pageable pageable);
}
