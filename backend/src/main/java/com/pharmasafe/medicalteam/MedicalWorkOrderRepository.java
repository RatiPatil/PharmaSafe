package com.pharmasafe.medicalteam;

public interface MedicalWorkOrderRepository
    extends org.springframework.data.jpa.repository.JpaRepository<MedicalWorkOrder, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<MedicalWorkOrder> {}
