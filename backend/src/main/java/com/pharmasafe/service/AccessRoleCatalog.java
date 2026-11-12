package com.pharmasafe.service;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.time.Instant;
import java.util.*;
@Component
public class AccessRoleCatalog {
 private final byte[] key=new byte[32];
 public AccessRoleCatalog(){new SecureRandom().nextBytes(key);}
 public static final Map<String,List<String>> FIELDS=Map.of(
 "ROLE_CASE_PROCESSOR",List.of("processingDepartment","businessUnit","countryRegion","processingExperience","training"),
 "ROLE_MEDICAL_REVIEWER",List.of("medicalQualification","specialization","medicalRegistrationNumber","yearsExperience","reviewDepartment"),
 "ROLE_QUALITY_REVIEWER",List.of("qualityDepartment","qualityExperience","certifications","businessUnit"),
 "ROLE_SAFETY_MANAGER",List.of("managementDepartment","managementExperience","businessUnit","responsibilities"),
 "ROLE_REPORTING_SPECIALIST",List.of("reportingDepartment","countryRegion","reportingExperience","training"),
 "ROLE_AUDITOR",List.of("auditOrganization","auditExperience","auditScope","certifications"));
 public static final Map<String,String> NAMES=Map.of("ROLE_CASE_PROCESSOR","Case Processor","ROLE_MEDICAL_REVIEWER","Medical Reviewer","ROLE_QUALITY_REVIEWER","Quality Reviewer","ROLE_SAFETY_MANAGER","Safety Manager","ROLE_REPORTING_SPECIALIST","Reporting Specialist","ROLE_AUDITOR","Auditor / Viewer");
 private String sign(String payload){try {var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(GeneralSecurityException e){throw new IllegalStateException("Selection unavailable");}}
 public String issue(String role){if(!FIELDS.containsKey(role))throw new IllegalArgumentException("Select a supported account type");String p=role+":"+Instant.now().plusSeconds(1800).getEpochSecond()+":"+UUID.randomUUID();return p+"."+sign(p);}
 public String verify(String token){try{int i=token.lastIndexOf('.');String p=token.substring(0,i);if(!MessageDigest.isEqual(sign(p).getBytes(),token.substring(i+1).getBytes()))throw new IllegalArgumentException();String[] a=p.split(":");if(a.length!=3||!FIELDS.containsKey(a[0])||Long.parseLong(a[1])<Instant.now().getEpochSecond())throw new IllegalArgumentException();return a[0];}catch(Exception e){throw new IllegalArgumentException("Account selection expired or invalid. Select your account type again.");}}
 public List<Map<String,Object>> roles(){return NAMES.keySet().stream().sorted().map(r->Map.<String,Object>of("role",r,"name",NAMES.get(r),"fields",FIELDS.get(r))).toList();}
}
