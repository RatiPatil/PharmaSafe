package com.pharmasafe.administration;

import com.pharmasafe.entity.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "role_admin_assignments")
public class RoleAdminAssignment {
  @Id
  @Column(name = "application_role", length = 32)
  public String applicationRole;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "administrator_id", unique = true)
  public User administrator;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "assigned_by")
  public User assignedBy;

  @Column(name = "assigned_at")
  public Instant assignedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  public long rowVersion;

  public RoleAdminAssignment() {}

  public RoleAdminAssignment(String role) {
    applicationRole = role;
  }
}
