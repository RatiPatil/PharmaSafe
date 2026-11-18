package com.pharmasafe.analytics;

import org.springframework.data.jpa.repository.*;

public interface PvCaseSeriesRepository
    extends JpaRepository<PvCaseSeries, String>, JpaSpecificationExecutor<PvCaseSeries> {}
