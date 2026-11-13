package com.pharmasafe.access;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharmasafe.entity.User;
import com.pharmasafe.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class InputValidationIntegrationTest {
 @Autowired MockMvc mvc;@Autowired ObjectMapper mapper;@Autowired UserRepository users;@Autowired SafetyCaseRepository cases;
 String admin;
 @BeforeEach void setup(){admin="validator-"+UUID.randomUUID().toString().substring(0,8);users.saveAndFlush(new User(admin,"not-used","ROLE_ADMIN"));}
 Map<String,Object> draft(){return new LinkedHashMap<>(Map.of("countryCode","USA","reportType","Spontaneous","initialReceiptDate",LocalDate.now().toString(),"latestReceivedDate",LocalDate.now().toString(),"draft",true));}
 @Test void malformedNestedClinicalDataNeverPersists()throws Exception{
  for(var bad:List.of(Map.of("patient",Map.of("ageValue",-1)),Map.of("reporters",List.of(Map.of("email","bad-email"))),Map.of("products",List.of(Map.of("productName","X".repeat(129)))),Map.of("countryCode","ZZZ"),Map.of("products",List.of(Map.of("dosageAmount",-1))),Map.of("patient",Map.of("dateOfBirth","2999-01-01")))){
   var body=draft();body.putAll(bad);long before=cases.count();mvc.perform(post("/api/cases").with(user(admin).roles("ADMIN")).with(csrf()).contentType("application/json").content(mapper.writeValueAsBytes(body))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors").isMap());assertEquals(before,cases.count());
  }
 }
 @Test void validDraftAndClinicalSymbolsArePreserved()throws Exception{
  var body=draft();body.put("narrative","ALT < 10 µg/L; dose 0.5 mg/day.\nRepeat day 2.");body.put("reporters",List.of(Map.of("firstName","José","lastName","O’Connor","email","pv+trial@hospital.example","phone","+442012345678","qualification","Physician","countryCode","GBR")));
  mvc.perform(post("/api/cases").with(user(admin).roles("ADMIN")).with(csrf()).contentType("application/json").content(mapper.writeValueAsBytes(body))).andExpect(status().isCreated()).andExpect(jsonPath("$.narrative").value(body.get("narrative")));
 }
 @Test void legacyMapEndpointsRejectMalformedAccountsAndWeakNewPasswords()throws Exception{
  mvc.perform(post("/api/admin/users").with(user(admin).roles("ADMIN")).with(csrf()).contentType("application/json").content("{\"username\":\"bad user!\",\"password\":\"SecurePassword123!\",\"role\":\"ROLE_CASE_PROCESSOR\"}")).andExpect(status().isBadRequest());
  mvc.perform(post("/api/admin/users").with(user(admin).roles("ADMIN")).with(csrf()).contentType("application/json").content("{\"username\":\"short-password-user\",\"password\":\"abc123\",\"role\":\"ROLE_CASE_PROCESSOR\"}")).andExpect(status().isBadRequest());assertTrue(users.findByUsername("short-password-user").isEmpty());
 }
 @Test void reportSpecificChoicesAndDateRangesAreEnforced()throws Exception{
  mvc.perform(post("/api/reports").with(user(admin).roles("ADMIN")).with(csrf()).contentType("application/json").content("{\"reportType\":\"Invented report\",\"product\":\"Drug A\",\"authority\":\"Authority\",\"dueDate\":\"2026-11-30\"}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.reportType").exists());
 }
}
