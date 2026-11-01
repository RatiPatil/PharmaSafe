package com.pharmasafe.entity;
import jakarta.persistence.*;
import java.time.*;
@Entity @Table(name="case_follow_ups")
public class CaseFollowUp {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="case_id",nullable=false)
    private SafetyCase safetyCase;
    @Column(name="follow_up_number",nullable=false)
    private int followUpNumber;
    @Column(nullable=false,length=16)
    private String kind;
    @Column(name="received_date",nullable=false)
    private LocalDate receivedDate;
    @Column(name="safety_received_date")
    private LocalDate safetyReceivedDate;
    @Column(nullable=false)
    private boolean significant;
    @Column(nullable=false,columnDefinition="TEXT")
    private String justification;
    @Column(nullable=false,columnDefinition="TEXT")
    private String summary;
    @Column(name="source_reference",length=255)
    private String sourceReference;
    @Column(name="previous_case_version",nullable=false)
    private int previousCaseVersion;
    @Column(name="case_version",nullable=false)
    private int caseVersion;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="related_action_item_id")
    private CaseActionItem relatedActionItem;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="created_by_user_id",nullable=false)
    private User createdByUser;
    @Column(name="created_by_username",nullable=false,length=64)
    private String createdByUsername;
    @Column(name="created_at",nullable=false,updatable=false)
    private Instant createdAt;
    @PrePersist void onCreate() { createdAt=Instant.now(); }
    public Long getId() { return id; }
    public SafetyCase getSafetyCase() { return safetyCase; }
    public void setSafetyCase(SafetyCase value) { safetyCase=value; }
    public int getFollowUpNumber() { return followUpNumber; }
    public void setFollowUpNumber(int value) { followUpNumber=value; }
    public String getKind() { return kind; }
    public void setKind(String value) { kind=value; }
    public LocalDate getReceivedDate() { return receivedDate; }
    public void setReceivedDate(LocalDate value) { receivedDate=value; }
    public LocalDate getSafetyReceivedDate() { return safetyReceivedDate; }
    public void setSafetyReceivedDate(LocalDate value) { safetyReceivedDate=value; }
    public boolean getSignificant() { return significant; }
    public void setSignificant(boolean value) { significant=value; }
    public String getJustification() { return justification; }
    public void setJustification(String value) { justification=value; }
    public String getSummary() { return summary; }
    public void setSummary(String value) { summary=value; }
    public String getSourceReference() { return sourceReference; }
    public void setSourceReference(String value) { sourceReference=value; }
    public int getPreviousCaseVersion() { return previousCaseVersion; }
    public void setPreviousCaseVersion(int value) { previousCaseVersion=value; }
    public int getCaseVersion() { return caseVersion; }
    public void setCaseVersion(int value) { caseVersion=value; }
    public CaseActionItem getRelatedActionItem() { return relatedActionItem; }
    public void setRelatedActionItem(CaseActionItem value) { relatedActionItem=value; }
    public User getCreatedByUser() { return createdByUser; }
    public void setCreatedByUser(User value) { createdByUser=value; }
    public String getCreatedByUsername() { return createdByUsername; }
    public void setCreatedByUsername(String value) { createdByUsername=value; }
    public Instant getCreatedAt() { return createdAt; }
}
