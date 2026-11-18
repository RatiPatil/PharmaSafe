package com.pharmasafe.education;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "edu_submission_documents")
public class EduDocument {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "submission_id")
  public EduSubmission submission;

  @Column(name = "file_name", length = 180, nullable = false)
  public String fileName;

  @Column(name = "file_bytes", columnDefinition = "bytea", nullable = false)
  public byte[] bytes;

  @Column(name = "sha256", length = 64, nullable = false)
  public String sha256;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
