package com.pharmasafe.processing;

import com.pharmasafe.entity.*;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "cp_team_members")
public class ProcessingMember {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "team_id")
  public ProcessingTeam team;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id")
  public User user;

  @Column(nullable = false, length = 16)
  public String status;

  @Column(name = "joined_at", nullable = false)
  public Instant joinedAt;
}
