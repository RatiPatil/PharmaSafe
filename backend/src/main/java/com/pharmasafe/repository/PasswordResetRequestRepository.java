package com.pharmasafe.repository;
import com.pharmasafe.entity.PasswordResetRequest;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface PasswordResetRequestRepository extends JpaRepository<PasswordResetRequest,Long>, JpaSpecificationExecutor<PasswordResetRequest> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from PasswordResetRequest r where r.id=:id")
    Optional<PasswordResetRequest> lockById(@Param("id") Long id);
    Optional<PasswordResetRequest> findByTokenHash(String tokenHash);
    long countByUserIdAndCreatedAtAfter(Long userId, java.time.Instant after);
}
