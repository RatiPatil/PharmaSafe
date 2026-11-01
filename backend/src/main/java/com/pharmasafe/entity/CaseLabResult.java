package com.pharmasafe.entity;
import jakarta.persistence.*;
@Entity @Table(name="case_lab_results")
public class CaseLabResult {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="case_id",nullable=false) private SafetyCase safetyCase;
 public Long getId(){return id;} public SafetyCase getSafetyCase(){return safetyCase;} public void setSafetyCase(SafetyCase v){safetyCase=v;}
 @Column(nullable=false,length=200) private String testName;
 public String getTestName(){return testName;} public void setTestName(String v){testName=v;}
 @Column(nullable=false) private java.time.LocalDate testDate;
 public java.time.LocalDate getTestDate(){return testDate;} public void setTestDate(java.time.LocalDate v){testDate=v;}
 @Column(nullable=false,length=1000) private String resultText;
 public String getResultText(){return resultText;} public void setResultText(String v){resultText=v;}
 @Column(length=80) private String unit;
 public String getUnit(){return unit;} public void setUnit(String v){unit=v;}
 @Column(precision=18,scale=6) private java.math.BigDecimal normalLow;
 public java.math.BigDecimal getNormalLow(){return normalLow;} public void setNormalLow(java.math.BigDecimal v){normalLow=v;}
 @Column(precision=18,scale=6) private java.math.BigDecimal normalHigh;
 public java.math.BigDecimal getNormalHigh(){return normalHigh;} public void setNormalHigh(java.math.BigDecimal v){normalHigh=v;}
 @Column(nullable=false,length=20) private String interpretation;
 public String getInterpretation(){return interpretation;} public void setInterpretation(String v){interpretation=v;}
 @Column(length=4000) private String notes;
 public String getNotes(){return notes;} public void setNotes(String v){notes=v;}
 @Column(nullable=false,length=64) private String recordedBy;
 public String getRecordedBy(){return recordedBy;} public void setRecordedBy(String v){recordedBy=v;}
 @Column(nullable=false) private java.time.Instant recordedAt;
 public java.time.Instant getRecordedAt(){return recordedAt;} public void setRecordedAt(java.time.Instant v){recordedAt=v;}
 @Column(length=1000) private String voidReason;
 public String getVoidReason(){return voidReason;} public void setVoidReason(String v){voidReason=v;}
 @Column(length=64) private String voidedBy;
 public String getVoidedBy(){return voidedBy;} public void setVoidedBy(String v){voidedBy=v;}
  private java.time.Instant voidedAt;
 public java.time.Instant getVoidedAt(){return voidedAt;} public void setVoidedAt(java.time.Instant v){voidedAt=v;}
}
