package com.pharmasafe.access;
import com.pharmasafe.entity.*;
import com.pharmasafe.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDate;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:role_statistics;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;INIT=CREATE SEQUENCE IF NOT EXISTS case_number_seq START WITH 1001")
@AutoConfigureMockMvc @ActiveProfiles("test")
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class DashboardStatisticsTest {
 @Autowired MockMvc mvc;@Autowired UserRepository users;@Autowired SafetyCaseRepository cases;
 @BeforeEach void data(){cases.deleteAll();users.deleteAll();User processor=users.saveAndFlush(new User("statistics-processor","hash","ROLE_CASE_PROCESSOR"));User other=users.saveAndFlush(new User("statistics-other","hash","ROLE_CASE_PROCESSOR"));
  for(int i=0;i<2;i++){SafetyCase c=new SafetyCase();c.setCaseNumber("STATS-"+i);c.setCountryCode(i==0?"IND":"USA");c.setReportType("Spontaneous");c.setInitialReceiptDate(LocalDate.now().minusDays(1));c.setLatestReceivedDate(LocalDate.now());c.setWorkflowState(i==0?"Data Entry":"Medical Review");c.setAssignedToUser(i==0?processor:other);c.setCreatedByUser(i==0?processor:other);cases.saveAndFlush(c);}
 }
 @Test void processorStatisticsAreScopedToOwnedOrAssignedCases()throws Exception{
  mvc.perform(get("/api/workspace/overview").with(user("statistics-processor").roles("CASE_PROCESSOR"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalCases").value(1)).andExpect(jsonPath("$.countryDistribution.IND").value(1)).andExpect(jsonPath("$.countryDistribution.USA").doesNotExist()).andExpect(jsonPath("$.receivedLast7Days").value(1)).andExpect(jsonPath("$.receiptActivity['"+LocalDate.now()+"']").value(1)).andExpect(jsonPath("$.roleDistribution").doesNotExist()).andExpect(jsonPath("$.pendingResetRequests").doesNotExist());
 }
 @Test void reviewerStatisticsUseReviewQueueAndAdminHasAccountStatistics()throws Exception{
  users.saveAndFlush(new User("reviewer","hash","ROLE_MEDICAL_REVIEWER"));
  mvc.perform(get("/api/workspace/overview").with(user("reviewer").roles("MEDICAL_REVIEWER"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalCases").value(1)).andExpect(jsonPath("$.countryDistribution.USA").value(1)).andExpect(jsonPath("$.countryDistribution.IND").doesNotExist());
  mvc.perform(get("/api/workspace/overview").with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalCases").value(2)).andExpect(jsonPath("$.accountStatusDistribution.APPROVED").value(3)).andExpect(jsonPath("$.roleDistribution.ROLE_CASE_PROCESSOR").value(2));
 }
}
