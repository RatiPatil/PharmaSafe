package com.pharmasafe.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class CaseNumberGeneratorService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public String generateCaseNumber(String countryCode) {
        String country = (countryCode != null && !countryCode.trim().isEmpty())
                ? countryCode.trim().toUpperCase()
                : "USA";
        int year = LocalDate.now().getYear();

        Object result = entityManager.createNativeQuery("SELECT nextval('case_number_seq')").getSingleResult();
        long seqVal = ((Number) result).longValue();

        return String.format("PS-%d-%s-%05d", year, country, seqVal);
    }
}
