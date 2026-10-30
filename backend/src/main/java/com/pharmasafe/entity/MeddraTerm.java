package com.pharmasafe.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "meddra_terms", indexes = {
    @Index(name = "idx_meddra_code", columnList = "code", unique = true),
    @Index(name = "idx_meddra_term", columnList = "term"),
    @Index(name = "idx_meddra_soc", columnList = "soc"),
    @Index(name = "idx_meddra_level", columnList = "level")
})
public class MeddraTerm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 255)
    private String term;

    @Column(nullable = false, length = 255)
    private String soc;

    @Column(length = 255)
    private String hlgt;

    @Column(length = 255)
    private String hlt;

    @Column(length = 255)
    private String llt;

    @Column(nullable = false, length = 16)
    private String level = "PT";

    @Column(nullable = false, length = 16)
    private String status = "Active";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public MeddraTerm() {
    }

    public MeddraTerm(String code, String term, String soc, String hlgt, String hlt, String llt, String level, String status) {
        this.code = code;
        this.term = term;
        this.soc = soc;
        this.hlgt = hlgt;
        this.hlt = hlt;
        this.llt = llt;
        this.level = level != null ? level : "PT";
        this.status = status != null ? status : "Active";
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getTerm() {
        return term;
    }

    public void setTerm(String term) {
        this.term = term;
    }

    public String getSoc() {
        return soc;
    }

    public void setSoc(String soc) {
        this.soc = soc;
    }

    public String getHlgt() {
        return hlgt;
    }

    public void setHlgt(String hlgt) {
        this.hlgt = hlgt;
    }

    public String getHlt() {
        return hlt;
    }

    public void setHlt(String hlt) {
        this.hlt = hlt;
    }

    public String getLlt() {
        return llt;
    }

    public void setLlt(String llt) {
        this.llt = llt;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
