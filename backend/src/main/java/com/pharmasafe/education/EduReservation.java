package com.pharmasafe.education;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "edu_student_id_reservations")
public class EduReservation {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "college_id")
  public EduCollege college;

  @Column(name = "token_hash", length = 64, nullable = false)
  public String tokenHash;

  @Column(name = "expires_at", nullable = false)
  public Instant expiresAt;

  @Column(nullable = false)
  public boolean consumed;

  @Column(name = "student_code", length = 32)
  public String studentCode;

  public String studentCode() {
    return studentCode;
  }
}
