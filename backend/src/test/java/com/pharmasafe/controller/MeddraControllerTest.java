package com.pharmasafe.controller;

import com.pharmasafe.entity.MeddraTerm;
import com.pharmasafe.repository.MeddraTermRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class MeddraControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeddraTermRepository meddraTermRepository;

    @BeforeEach
    void setUp() {
        if (meddraTermRepository.count() == 0) {
            meddraTermRepository.save(new MeddraTerm("PT100001", "Hepatitis", "Hepatobiliary disorders", "Hepatic disorders", "Hepatitis", "Hepatitis NEC", "PT", "Active"));
            meddraTermRepository.save(new MeddraTerm("PT200001", "Rash", "Skin and subcutaneous tissue disorders", "Epidermal conditions", "Rashes", "Rash NEC", "PT", "Active"));
            meddraTermRepository.save(new MeddraTerm("PT400001", "Myocardial infarction", "Cardiac disorders", "Coronary disorders", "Ischaemic", "Myocardial infarction", "PT", "Active"));
        }
    }

    @Test
    @DisplayName("Unauthenticated request to MedDRA search is rejected with 401")
    void searchTerms_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/meddra/search?query=Hepatitis"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "analyst", roles = {"CASE_PROCESSOR"})
    @DisplayName("Authenticated user can search MedDRA terms by keyword")
    void searchTerms_authenticated_returnsMatchingTerms() throws Exception {
        mockMvc.perform(get("/api/meddra/search")
                        .param("query", "Rash")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.content[0].term", containsStringIgnoringCase("Rash")));
    }

    @Test
    @WithMockUser(username = "analyst", roles = {"CASE_PROCESSOR"})
    @DisplayName("Authenticated user can retrieve distinct System Organ Classes (SOCs)")
    void getDistinctSocs_returnsList() throws Exception {
        mockMvc.perform(get("/api/meddra/socs")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasItem("Hepatobiliary disorders")))
                .andExpect(jsonPath("$", hasItem("Cardiac disorders")));
    }

    @Test
    @WithMockUser(username = "analyst", roles = {"CASE_PROCESSOR"})
    @DisplayName("Lookup MedDRA term by exact code returns term details")
    void getTermByCode_existing_returnsDetails() throws Exception {
        mockMvc.perform(get("/api/meddra/PT100001")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("PT100001")))
                .andExpect(jsonPath("$.term", is("Hepatitis")));
    }
}
