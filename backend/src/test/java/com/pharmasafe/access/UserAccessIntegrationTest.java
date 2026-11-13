package com.pharmasafe.access;
import com.fasterxml.jackson.databind.*;
import com.pharmasafe.entity.*;
import com.pharmasafe.repository.*;
import com.pharmasafe.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.mock.web.MockHttpSession;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class UserAccessIntegrationTest {
 @Autowired com.pharmasafe.validation.InputRules inputRules;
 @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired UserRepository users;
 @Autowired RegistrationRequestRepository registrations;@Autowired PasswordResetRequestRepository resets;
 @Autowired UserAccessService access;@Autowired AccessRoleCatalog catalog;@Autowired PasswordEncoder encoder;
 @MockBean AccessRateLimiter limiter;@MockBean AccessMailService mail;
 static final String PASSWORD="SyntheticAccess123!";
 String admin;
 @BeforeEach void admin(){admin="access-admin-"+UUID.randomUUID().toString().substring(0,8);users.saveAndFlush(new User(admin,encoder.encode(PASSWORD),"ROLE_ADMIN"));}
 Map<String,Object> body(String role){String name="access-"+UUID.randomUUID().toString().substring(0,8);Map<String,String> details=new HashMap<>();AccessRoleCatalog.FIELDS.get(role).forEach(f->{var choices=inputRules.registrationChoices().path(f);details.put(f,choices.isArray()?choices.get(0).asText():"REG-123");});return new HashMap<>(Map.ofEntries(Map.entry("selectionToken",catalog.issue(role)),Map.entry("fullName","Synthetic User"),Map.entry("employeeId",name),Map.entry("email",name+"@example.test"),Map.entry("mobileNumber","+12345678901"),Map.entry("department","Safety"),Map.entry("designation","Specialist"),Map.entry("organization","Synthetic Org"),Map.entry("username",name),Map.entry("password",PASSWORD),Map.entry("confirmPassword",PASSWORD),Map.entry("reason","Synthetic access for assigned role"),Map.entry("details",details)));}
 JsonNode register(Map<String,Object> body)throws Exception{return json.readTree(mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(json.writeValueAsBytes(body))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());}
 ResultActions login(String name,String password)throws Exception{return mvc.perform(post("/api/auth/login").contentType("application/json").content(json.writeValueAsBytes(Map.of("username",name,"password",password))));}
 @Test void allSixRolesRequireApprovalAndKeepOriginalRole()throws Exception{
  for(String role:AccessRoleCatalog.FIELDS.keySet()){
   var b=body(role);long id=register(b).get("requestId").asLong();String name=(String)b.get("username");
   assertFalse(users.findByUsername(name).orElseThrow().isEnabled());
   assertNotEquals(PASSWORD,users.findByUsername(name).orElseThrow().getPasswordHash());
   login(name,PASSWORD).andExpect(status().isForbidden());
   mvc.perform(put("/api/admin/registration-requests/"+id+"/approve").with(user(admin).roles("ADMIN")).with(csrf()).contentType("application/json").content("{\"reason\":\"Training verified\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.role").value(role)).andExpect(jsonPath("$.accountStatus").value("APPROVED"));
   login((String)b.get("email"),PASSWORD).andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value(role));
  }
 }
 @Test void rejectsRoleTamperingAndPublicAdminAndRequiresCsrf()throws Exception{
  mvc.perform(get("/api/auth/access-roles")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("ROLE_ADMIN"))));
  mvc.perform(post("/api/auth/role-selection").with(csrf()).contentType("application/json").content("{\"role\":\"ROLE_ADMIN\"}")).andExpect(status().isBadRequest());
  var b=body("ROLE_MEDICAL_REVIEWER");b.put("selectionToken",((String)b.get("selectionToken")).replace("ROLE_MEDICAL_REVIEWER","ROLE_ADMIN"));
  mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(json.writeValueAsBytes(b))).andExpect(status().isBadRequest());
  mvc.perform(post("/api/auth/register").contentType("application/json").content(json.writeValueAsBytes(body("ROLE_AUDITOR")))).andExpect(status().isForbidden());
  mvc.perform(get("/api/admin/registration-requests").with(user("processor").roles("CASE_PROCESSOR"))).andExpect(status().isForbidden());
  mvc.perform(post("/api/cases/1/assessments").with(user("processor").roles("CASE_PROCESSOR")).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
 }
 @Test void rejectsDuplicatesInvalidEmailWeakPasswordAndWrongRoleFields()throws Exception{
  var b=body("ROLE_CASE_PROCESSOR");register(b);
  mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(json.writeValueAsBytes(b))).andExpect(status().isBadRequest());
  for(var entry:Map.of("email","invalid-email","password","short","details",Map.of("medicalQualification","Invalid role field")).entrySet()){
   var bad=body("ROLE_CASE_PROCESSOR");bad.put(entry.getKey(),entry.getValue());mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(json.writeValueAsBytes(bad))).andExpect(status().isBadRequest());
  }
 }
 @Test void rejectionAndSuspensionCannotBeBypassedByLegacyToggleAndSessionIsRevoked()throws Exception{
  var b=body("ROLE_CASE_PROCESSOR");long id=register(b).get("requestId").asLong();var u=users.findByUsername((String)b.get("username")).orElseThrow();
  access.decideRegistration(id,"reject","Not eligible",admin);login(u.getUsername(),PASSWORD).andExpect(status().isForbidden());
  mvc.perform(patch("/api/admin/users/"+u.getId()+"/toggle-status").with(user(admin).roles("ADMIN")).with(csrf())).andExpect(status().isBadRequest());
  var c=body("ROLE_CASE_PROCESSOR");long rid=register(c).get("requestId").asLong();access.decideRegistration(rid,"approve","Verified",admin);
  var result=login((String)c.get("username"),PASSWORD).andExpect(status().isOk()).andReturn();var session=(MockHttpSession)result.getRequest().getSession(false);
  var approved=users.findByUsername((String)c.get("username")).orElseThrow();access.accountStatus(approved.getId(),"suspend","Access paused",admin);
  mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());login(approved.getUsername(),PASSWORD).andExpect(status().isForbidden());
  access.accountStatus(approved.getId(),"activate","Access restored",admin);login(approved.getUsername(),PASSWORD).andExpect(status().isOk());
 }
 @Test void recoveryIsSingleUseExpiresAndRevokesOldPasswordAndSessions()throws Exception{
  var b=body("ROLE_MEDICAL_REVIEWER");long rid=register(b).get("requestId").asLong();access.decideRegistration(rid,"approve","Verified",admin);
  String name=(String)b.get("username");var session=(MockHttpSession)login(name,PASSWORD).andExpect(status().isOk()).andReturn().getRequest().getSession(false);
  mvc.perform(post("/api/auth/forgot-password").with(csrf()).contentType("application/json").content(json.writeValueAsBytes(Map.of("identifier",b.get("email"))))).andExpect(status().isOk());
  var u=users.findByUsername(name).orElseThrow();var req=resets.findAll().stream().filter(r->r.user.getId().equals(u.getId())).findFirst().orElseThrow();
  access.decideReset(req.id,"approve","Identity verified",admin);
  var token=org.mockito.ArgumentCaptor.forClass(String.class);verify(mail).sendReset(eq(u.getEmail()),token.capture());
  mvc.perform(get("/api/admin/password-reset-requests").with(user(admin).roles("ADMIN"))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(token.getValue()))));
  String next="UpdatedSynthetic123!";access.reset(token.getValue(),next,next);
  assertThrows(IllegalArgumentException.class,()->access.reset(token.getValue(),next,next));
  login(name,PASSWORD).andExpect(status().isUnauthorized());login(name,next).andExpect(status().isOk());
  mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
  access.forgot(name);var pending=resets.findAll().stream().filter(r->r.user.getId().equals(u.getId())&&r.status.equals("PENDING")).findFirst().orElseThrow();
  access.decideReset(pending.id,"approve","Verified again",admin);var all=org.mockito.ArgumentCaptor.forClass(String.class);verify(mail,times(2)).sendReset(eq(u.getEmail()),all.capture());
  var expired=resets.findById(pending.id).orElseThrow();expired.expiresAt=Instant.now().minusSeconds(1);resets.saveAndFlush(expired);
  assertThrows(IllegalArgumentException.class,()->access.reset(all.getAllValues().get(1),next,next));
 }
 @Test void interveningAdministratorPasswordChangeInvalidatesIssuedLink()throws Exception{
  var b=body("ROLE_AUDITOR");long rid=register(b).get("requestId").asLong();access.decideRegistration(rid,"approve","Verified",admin);access.forgot((String)b.get("username"));
  var u=users.findByUsername((String)b.get("username")).orElseThrow();var req=resets.findAll().stream().filter(r->r.user.getId().equals(u.getId())).findFirst().orElseThrow();access.decideReset(req.id,"approve","Verified",admin);
  var token=org.mockito.ArgumentCaptor.forClass(String.class);verify(mail).sendReset(eq(u.getEmail()),token.capture());
  mvc.perform(patch("/api/admin/users/"+u.getId()+"/reset-password").with(user(admin).roles("ADMIN")).with(csrf()).contentType("application/json").content("{\"newPassword\":\"AdminChangedPassword123!\"}")).andExpect(status().isOk());
  assertThrows(IllegalArgumentException.class,()->access.reset(token.getValue(),PASSWORD,PASSWORD));
 }
 @Test void mailFailureKeepsRequestPendingAndUnknownRecoveryIsGeneric()throws Exception{
  var b=body("ROLE_QUALITY_REVIEWER");long rid=register(b).get("requestId").asLong();access.decideRegistration(rid,"approve","Verified",admin);access.forgot((String)b.get("username"));
  var u=users.findByUsername((String)b.get("username")).orElseThrow();var req=resets.findAll().stream().filter(r->r.user.getId().equals(u.getId())).findFirst().orElseThrow();
  doThrow(new IllegalStateException("Mail unavailable")).when(mail).sendReset(anyString(),anyString());assertThrows(IllegalStateException.class,()->access.decideReset(req.id,"approve","Verified",admin));assertEquals("PENDING",resets.findById(req.id).orElseThrow().status);assertNull(resets.findById(req.id).orElseThrow().tokenHash);
  mvc.perform(post("/api/auth/forgot-password").with(csrf()).contentType("application/json").content("{\"identifier\":\"does-not-exist\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.message").value("If an eligible account exists, your request will be reviewed by an administrator."));
 }
 @Test void accountDetailsIncludeRegistrationAndLegacyDatesWithoutCredentials()throws Exception{
  var b=body("ROLE_MEDICAL_REVIEWER");register(b);var u=users.findByUsername((String)b.get("username")).orElseThrow();
  mvc.perform(get("/api/admin/access-users/"+u.getId()).with(user(admin).roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.details.medicalQualification").value("MBBS")).andExpect(jsonPath("$.reason").value(b.get("reason"))).andExpect(jsonPath("$.createdAt").isNotEmpty()).andExpect(jsonPath("$.passwordHash").doesNotExist());
  var legacy=users.findByUsername(admin).orElseThrow();mvc.perform(get("/api/admin/access-users/"+legacy.getId()).with(user(admin).roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.createdAt").isNotEmpty()).andExpect(jsonPath("$.role").value("ROLE_ADMIN")).andExpect(jsonPath("$.passwordHash").doesNotExist());
  mvc.perform(get("/api/admin/access-users/"+u.getId()).with(user("processor").roles("CASE_PROCESSOR"))).andExpect(status().isForbidden());
  mvc.perform(get("/api/admin/access-users/99999999").with(user(admin).roles("ADMIN"))).andExpect(status().isNotFound());
 }
 @Test void adminFiltersSortAndCompleteDetailsArePersisted()throws Exception{
  var b=body("ROLE_SAFETY_MANAGER");long id=register(b).get("requestId").asLong();
  mvc.perform(get("/api/admin/registration-requests").param("role","ROLE_SAFETY_MANAGER").param("status","PENDING").param("search",(String)b.get("email")).param("sort","fullName").param("direction","asc").with(user(admin).roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(id)).andExpect(jsonPath("$.content[0].details.managementDepartment").value("Drug Safety / Pharmacovigilance")).andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
 }
}
