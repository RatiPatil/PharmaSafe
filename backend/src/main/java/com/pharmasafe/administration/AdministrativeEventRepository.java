package com.pharmasafe.administration;

import org.springframework.data.jpa.repository.*;

public interface AdministrativeEventRepository
    extends JpaRepository<AdministrativeEvent, Long>,
        JpaSpecificationExecutor<AdministrativeEvent> {}
