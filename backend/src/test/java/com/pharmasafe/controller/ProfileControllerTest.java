package com.pharmasafe.controller;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharmasafe.entity.User;
import com.pharmasafe.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional @WithMockUser(username="own-profile",roles="CASE_PROCESSOR")
class ProfileControllerTest {
 @Autowired MockMvc mvc;@Autowired UserRepository users;@Autowired ObjectMapper json;User user;
 @BeforeEach void setup(){user=users.saveAndFlush(new User("own-profile","never-return-this-hash","ROLE_CASE_PROCESSOR"));}
 @Test void profileShowsOwnRoleWithoutSecrets()throws Exception{String response=mvc.perform(get("/api/profile")).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ROLE_CASE_PROCESSOR")).andReturn().getResponse().getContentAsString();assertFalse(response.contains("never-return-this-hash"));assertFalse(response.contains("passwordHash"));}
 @Test void updateCannotPromoteRoleAndRejectsStaleChanges()throws Exception{long version=user.getAccountRowVersion();var body=Map.of("fullName","Synthetic Analyst","department","Case operations","expectedAccountRowVersion",version,"role","ROLE_ADMIN");mvc.perform(patch("/api/profile").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ROLE_CASE_PROCESSOR"));assertEquals("ROLE_CASE_PROCESSOR",user.getRole());assertEquals(0,user.getAuthVersion());mvc.perform(patch("/api/profile").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isConflict());}
 @Test void profileWriteRequiresCsrf()throws Exception{mvc.perform(patch("/api/profile").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("fullName","Synthetic","department","Safety","expectedAccountRowVersion",user.getAccountRowVersion())))).andExpect(status().isForbidden());}
}
