package com.pharmasafe.entity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
@Entity @Table(name="case_document_exports")
public class CaseDocumentExport {
 @Id @Column(length=36) private String id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="case_id",nullable=false) private SafetyCase safetyCase;
 @Column(name="document_kind",nullable=false,length=24) private String documentKind;
 @Column(name="final_document",nullable=false) private boolean finalDocument;
 @Column(name="case_version",nullable=false) private int caseVersion;
 @Column(name="source_row_version",nullable=false) private long sourceRowVersion;
 @Column(name="workflow_state",nullable=false,length=32) private String workflowState;
 @Column(name="template_version",nullable=false,length=24) private String templateVersion;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="generated_by_user_id",nullable=false) private User generatedByUser;
 @Column(name="generated_by_username",nullable=false,length=64) private String generatedByUsername;
 @Column(name="generated_at",nullable=false) private Instant generatedAt;
 @JdbcTypeCode(SqlTypes.JSON) @Column(name="snapshot_json",nullable=false,columnDefinition="jsonb") private String snapshotJson;
 @Column(name="pdf_bytes",nullable=false,columnDefinition="bytea") private byte[] pdfBytes;
 @Column(name="pdf_sha256",nullable=false,length=64) private String pdfSha256;
 @Column(name="file_name",nullable=false,length=180) private String fileName;
 public String getId() {return id;}
 public void setId(String value) {id=value;}
 public SafetyCase getSafetyCase() {return safetyCase;}
 public void setSafetyCase(SafetyCase value) {safetyCase=value;}
 public String getDocumentKind() {return documentKind;}
 public void setDocumentKind(String value) {documentKind=value;}
 public boolean getFinalDocument() {return finalDocument;}
 public void setFinalDocument(boolean value) {finalDocument=value;}
 public int getCaseVersion() {return caseVersion;}
 public void setCaseVersion(int value) {caseVersion=value;}
 public long getSourceRowVersion() {return sourceRowVersion;}
 public void setSourceRowVersion(long value) {sourceRowVersion=value;}
 public String getWorkflowState() {return workflowState;}
 public void setWorkflowState(String value) {workflowState=value;}
 public String getTemplateVersion() {return templateVersion;}
 public void setTemplateVersion(String value) {templateVersion=value;}
 public User getGeneratedByUser() {return generatedByUser;}
 public void setGeneratedByUser(User value) {generatedByUser=value;}
 public String getGeneratedByUsername() {return generatedByUsername;}
 public void setGeneratedByUsername(String value) {generatedByUsername=value;}
 public Instant getGeneratedAt() {return generatedAt;}
 public void setGeneratedAt(Instant value) {generatedAt=value;}
 public String getSnapshotJson() {return snapshotJson;}
 public void setSnapshotJson(String value) {snapshotJson=value;}
 public byte[] getPdfBytes() {return pdfBytes;}
 public void setPdfBytes(byte[] value) {pdfBytes=value;}
 public String getPdfSha256() {return pdfSha256;}
 public void setPdfSha256(String value) {pdfSha256=value;}
 public String getFileName() {return fileName;}
 public void setFileName(String value) {fileName=value;}
}
