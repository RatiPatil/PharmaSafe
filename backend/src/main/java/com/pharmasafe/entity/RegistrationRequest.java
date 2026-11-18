package com.pharmasafe.entity;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="registration_requests")
public class RegistrationRequest {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="cp_team_id") public com.pharmasafe.processing.ProcessingTeam processingTeam;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="mr_team_id") public com.pharmasafe.medicalteam.MedicalTeam medicalTeam;
    @ManyToOne(optional=false) @JoinColumn(name="user_id") public User user;
    @Column(name="created_at",nullable=false) public Instant createdAt=Instant.now();
    @ManyToOne @JoinColumn(name="reviewed_by") public User reviewedBy;
    @Column(name="reviewed_at") public Instant reviewedAt;
    @Column(name="decision_reason",length=2000) public String decisionReason;
    @Version @Column(name="row_version") public long version;
    @Column(name="requested_role",nullable=false) public String requestedRole;
    @Column(nullable=false,length=2000) public String reason;
    @Column(name="additional_information",length=2000) public String additionalInformation;
    @ElementCollection(fetch=FetchType.EAGER)
    @CollectionTable(name="registration_details",joinColumns=@JoinColumn(name="request_id"))
    @MapKeyColumn(name="field_name",length=64) @Column(name="field_value",length=1000)
    public java.util.Map<String,String> details = new java.util.LinkedHashMap<>();
}
