package com.pharmasafe.service;

import com.pharmasafe.dto.CaseRequestDto;
import com.pharmasafe.dto.CaseResponseDto;
import com.pharmasafe.entity.SafetyCase;
import com.pharmasafe.repository.SafetyCaseRepository;
import com.pharmasafe.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CaseServiceTest {

    @Mock
    private SafetyCaseRepository safetyCaseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CaseNumberGeneratorService caseNumberGeneratorService;

    @Mock
    private AuditService auditService;

    @Mock private com.pharmasafe.repository.CaseFollowUpRepository followUps;
    @Mock private com.pharmasafe.repository.SafetyReportRepository reports;
    @Mock private CaseClosureGuard closureGuard;
    @InjectMocks
    private CaseService caseService;

    private CaseRequestDto validDto;

    @BeforeEach
    public void setup() {
        validDto = new CaseRequestDto();
        validDto.setCountryCode("USA");
        validDto.setReportType("Spontaneous");
        validDto.setInitialReceiptDate(LocalDate.of(2026, 10, 1));
        validDto.setLatestReceivedDate(LocalDate.of(2026, 10, 5));
        validDto.setPriority("Routine");

        // Reporter
        CaseRequestDto.ReporterDto r = new CaseRequestDto.ReporterDto();
        r.setLastName("Smith");
        r.setCountryCode("USA");
        validDto.setReporters(List.of(r));

        // Suspect Product
        CaseRequestDto.ProductDto p = new CaseRequestDto.ProductDto();
        p.setProductType("Suspect");
        p.setProductName("CardioGuard");
        validDto.setProducts(List.of(p));

        // Event
        CaseRequestDto.EventDto e = new CaseRequestDto.EventDto();
        e.setEventVerbatim("Headache");
        e.setOnsetDate(LocalDate.of(2026, 10, 2));
        validDto.setEvents(List.of(e));
    }

    @Test
    public void testCreateCaseSuccessfulExecution() {
        when(caseNumberGeneratorService.generateCaseNumber("USA")).thenReturn("PS-2026-USA-01001");
        when(safetyCaseRepository.saveAndFlush(any(SafetyCase.class))).thenAnswer(inv -> {
            SafetyCase sc = inv.getArgument(0);
            sc.setId(100L);
            return sc;
        });

        CaseResponseDto response = caseService.createCase(validDto, "testuser", "127.0.0.1", "TestAgent");

        assertNotNull(response);
        assertEquals("PS-2026-USA-01001", response.getCaseNumber());
        assertEquals("USA", response.getCountryCode());
        verify(auditService, times(1)).logCaseEvent(eq(100L), eq("CASE_CREATED"), eq("testuser"), eq("SUCCESS"), any(), eq("127.0.0.1"), eq("TestAgent"));
    }

    @Test
    public void testCreateCaseFailsWhenReceiptDatesInverted() {
        validDto.setInitialReceiptDate(LocalDate.of(2026, 10, 10));
        validDto.setLatestReceivedDate(LocalDate.of(2026, 10, 1));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                caseService.createCase(validDto, "testuser", "127.0.0.1", "TestAgent"));

        assertTrue(ex.getMessage().contains("Latest received date cannot be before initial receipt date"));
        verifyNoInteractions(safetyCaseRepository);
    }

    @Test
    public void testCreateNonDraftCaseFailsWhenNoReporter() {
        validDto.setReporters(new ArrayList<>());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                caseService.createCase(validDto, "testuser", "127.0.0.1", "TestAgent"));

        assertTrue(ex.getMessage().contains("At least one reporter is required"));
        verifyNoInteractions(safetyCaseRepository);
    }

    @Test
    public void testCreateNonDraftCaseFailsWhenNoSuspectProduct() {
        CaseRequestDto.ProductDto concomitant = new CaseRequestDto.ProductDto();
        concomitant.setProductType("Concomitant");
        concomitant.setProductName("Aspirin");
        validDto.setProducts(List.of(concomitant));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                caseService.createCase(validDto, "testuser", "127.0.0.1", "TestAgent"));

        assertTrue(ex.getMessage().contains("At least one suspect product"));
        verifyNoInteractions(safetyCaseRepository);
    }

    @Test
    public void testCreateNonDraftCaseFailsWhenNoEvent() {
        validDto.setEvents(new ArrayList<>());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                caseService.createCase(validDto, "testuser", "127.0.0.1", "TestAgent"));

        assertTrue(ex.getMessage().contains("At least one adverse event is required"));
        verifyNoInteractions(safetyCaseRepository);
    }
}
