package com.pharmasafe.controller;
import com.pharmasafe.dto.*;
import com.pharmasafe.service.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/cases/{id}") @PreAuthorize("isAuthenticated()")
public class CaseFollowUpController {
 private final CaseFollowUpService service;
 public CaseFollowUpController(CaseFollowUpService service){this.service=service;}
 private String role(Authentication a){return a.getAuthorities().iterator().next().getAuthority();}
 @GetMapping("/follow-ups") public Page<FollowUpResponse> list(@PathVariable Long id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,Authentication a){
  return service.list(id,a.getName(),role(a),PageRequest.of(Math.max(0,page),Math.min(50,Math.max(1,size)),Sort.by(Sort.Direction.DESC,"followUpNumber")));
 }
 @GetMapping("/closure-readiness") public CaseClosureGuard.Readiness readiness(@PathVariable Long id,Authentication a){return service.readiness(id,a.getName(),role(a));}
 @PostMapping("/follow-ups") @PreAuthorize("hasAnyRole('ADMIN','SAFETY_MANAGER','CASE_PROCESSOR','USER')")
 public FollowUpResponse record(@PathVariable Long id,@Valid @RequestBody FollowUpRequest r,Authentication a){return service.record(id,r,a.getName(),role(a));}
 @PostMapping("/reopen") @PreAuthorize("hasAnyRole('ADMIN','SAFETY_MANAGER')")
 public CaseResponseDto reopen(@PathVariable Long id,@Valid @RequestBody ReopenCaseRequest r,Authentication a){return service.reopen(id,r,a.getName(),role(a));}
}
