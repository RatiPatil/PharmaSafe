package com.pharmasafe.processing;

import com.pharmasafe.entity.*;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "cp_teams")
public class ProcessingTeam {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false, length = 100)
  public String name;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "lead_user_id", unique = true)
  public User lead;

  @Column(name = "registration_key", nullable = false, length = 36)
  public String registrationKey;

  @Column(name = "registration_open", nullable = false)
  public boolean registrationOpen;

  @Column(nullable = false)
  public boolean active;

  @Column(name = "max_members", nullable = false)
  public int maxMembers;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "created_by_user_id")
  public User createdBy;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Version
  @Column(name = "row_version", nullable = false)
  public long rowVersion;

  public String code() {
    return "CP-TL-" + String.format(java.util.Locale.ROOT, "%03d", id);
  }
}
