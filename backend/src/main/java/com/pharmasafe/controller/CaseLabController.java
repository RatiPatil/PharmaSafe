package com.pharmasafe.controller;
import com.pharmasafe.dto.LabResultDto;
import com.pharmasafe.service.CaseLabService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/cases/{id}/laboratory") @PreAuthorize("isAuthenticated()")
public class CaseLabController {
 private final CaseLabService service; public CaseLabController(CaseLabService service){this.service=service;}
 private String role(Authentication a){return a.getAuthorities().iterator().next().getAuthority();}
 @GetMapping public List<LabResultDto> list(@PathVariable Long id,Authentication a){return service.list(id,a.getName(),role(a));}
 @PostMapping @PreAuthorize("hasAnyRole('ADMIN','SAFETY_MANAGER','CASE_PROCESSOR','USER')")
 public LabResultDto add(@PathVariable Long id,@Valid @RequestBody LabResultDto.Create r,Authentication a){return service.add(id,r,a.getName(),role(a));}
 @PostMapping("/{labId}/void") @PreAuthorize("hasAnyRole('ADMIN','SAFETY_MANAGER','CASE_PROCESSOR','USER')")
 public LabResultDto voidResult(@PathVariable Long id,@PathVariable Long labId,@Valid @RequestBody LabResultDto.Void r,Authentication a){return service.voidResult(id,labId,r,a.getName(),role(a));}
}
