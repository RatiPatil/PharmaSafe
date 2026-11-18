package com.pharmasafe.education;

import com.pharmasafe.entity.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "edu_students")
public class EduStudent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "user_id")
  public User user;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "college_id")
  public EduCollege college;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "reservation_id")
  public EduReservation reservation;

  @Column(name = "student_code", length = 32, nullable = false)
  public String studentCode;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Version
  @Column(name = "row_version", nullable = false)
  public long rowVersion;
}
