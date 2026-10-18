package com.pharmasafe.repository;

import com.pharmasafe.entity.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findTop50ByOrderByCreatedAtDesc();

    List<AuditEvent> findByUsernameOrderByCreatedAtDesc(String username);

    List<AuditEvent> findTop15ByEventTypeInOrderByCreatedAtDesc(List<String> eventTypes);

    List<AuditEvent> findTop15ByUsernameAndEventTypeInOrderByCreatedAtDesc(String username, List<String> eventTypes);
}
