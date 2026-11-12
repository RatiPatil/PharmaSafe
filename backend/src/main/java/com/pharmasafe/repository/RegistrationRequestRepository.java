package com.pharmasafe.repository;
import com.pharmasafe.entity.RegistrationRequest;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface RegistrationRequestRepository extends JpaRepository<RegistrationRequest,Long>, JpaSpecificationExecutor<RegistrationRequest> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from RegistrationRequest r where r.id=:id")
    Optional<RegistrationRequest> lockById(@Param("id") Long id);
}
