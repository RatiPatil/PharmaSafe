package com.pharmasafe.medicalteam;

import org.springframework.data.jpa.repository.*;

public interface MedicalCaseAllocationRepository
    extends JpaRepository<MedicalCaseAllocation, Long> {}
