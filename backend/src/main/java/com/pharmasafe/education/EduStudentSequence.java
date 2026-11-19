package com.pharmasafe.education;

import jakarta.persistence.*;

@Entity
@Table(name = "edu_college_student_sequences")
public class EduStudentSequence {
  @Id
  @Column(name = "college_id")
  public Long collegeId;

  @Column(name = "last_value", nullable = false)
  public long lastValue;
}
