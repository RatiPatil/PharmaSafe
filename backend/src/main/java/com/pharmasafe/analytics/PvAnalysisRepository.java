package com.pharmasafe.analytics;

public interface PvAnalysisRepository
    extends org.springframework.data.jpa.repository.JpaRepository<PvAnalysisRecord, String>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<PvAnalysisRecord> {}
