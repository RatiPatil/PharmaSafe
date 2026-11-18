package com.pharmasafe.education;

import com.pharmasafe.entity.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "edu_task_feedback")
public class EduFeedback {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "task_id")
  public EduTask task;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "submission_id")
  public EduSubmission submission;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "reviewer_id")
  public User reviewer;

  @Column(name = "outcome", length = 16, nullable = false)
  public String outcome;

  @Column(name = "feedback", length = 3000, nullable = false)
  public String feedback;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
