package com.pharmasafe.medicalteam;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicalAllocationEventRepository
    extends JpaRepository<MedicalAllocationEvent, Long> {}
