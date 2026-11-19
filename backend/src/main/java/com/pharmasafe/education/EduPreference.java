package com.pharmasafe.education;

import jakarta.persistence.*;

@Entity
@Table(name = "edu_preferences")
public class EduPreference {
  @Id
  @Column(name = "user_id")
  public Long userId;

  @Column(name = "compact_view", nullable = false)
  public boolean compactView;

  @Column(name = "reduce_motion", nullable = false)
  public boolean reduceMotion;
}
