package com.pharmasafe.processing;

public interface ProcessingWorkOrderRepository
    extends org.springframework.data.jpa.repository.JpaRepository<ProcessingWorkOrder, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<ProcessingWorkOrder> {}
