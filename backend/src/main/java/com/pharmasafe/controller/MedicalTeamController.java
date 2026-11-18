package com.pharmasafe.controller;

import com.pharmasafe.medicalteam.MedicalTeamService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/medical-teams")
public class MedicalTeamController {
  private final MedicalTeamService service;
  private final com.pharmasafe.medicalteam.MedicalCaseWorkbench workbench;

  public MedicalTeamController(
      MedicalTeamService s, com.pharmasafe.medicalteam.MedicalCaseWorkbench wb) {
    workbench = wb;
    service = s;
  }

  @GetMapping("/invitations/{key}")
  public Map<String, Object> invitation(@PathVariable String key) {
    return service.invitation(key);
  }

  @GetMapping("/invitations/code/{code}")
  public Map<String, Object> invitationByCode(@PathVariable String code) {
    return service.invitationByCode(code);
  }

  @GetMapping("/overview")
  public Map<String, Object> overview(
      @RequestParam(required = false) Long teamId, Authentication a) {
    return service.overview(teamId, a.getName());
  }

  @GetMapping("/teams")
  public List<Map<String, Object>> teams(Authentication a) {
    return service.listTeams(a.getName());
  }

  @PostMapping("/teams")
  public ResponseEntity<Map<String, Object>> create(
      @Valid @RequestBody MedicalTeamService.CreateTeam d, Authentication a) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.createTeam(d, a.getName()));
  }

  @PutMapping("/teams/{id}")
  public Map<String, Object> state(
      @PathVariable Long id, @Valid @RequestBody MedicalTeamService.TeamState d, Authentication a) {
    return service.teamState(id, d, a.getName());
  }

  @GetMapping("/teams/{id}/members")
  public Page<Map<String, Object>> members(
      @PathVariable Long id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      Authentication a) {
    return service.members(id, page, size, a.getName());
  }

  @GetMapping("/unassigned-employees")
  public Page<Map<String, Object>> unassigned(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      Authentication a) {
    return service.unassigned(page, size, a.getName());
  }

  @PostMapping("/teams/{id}/members")
  public Map<String, Object> member(
      @PathVariable Long id,
      @Valid @RequestBody MedicalTeamService.MemberRequest d,
      Authentication a) {
    return service.addMember(id, d, a.getName());
  }

  @GetMapping("/registrations")
  public Page<Map<String, Object>> registrations(
      @RequestParam(required = false) Long teamId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      Authentication a) {
    return service.registrations(teamId, page, size, a.getName());
  }

  @PostMapping("/registrations/{id}/{decision:approve|reject}")
  public Map<String, Object> decide(
      @PathVariable Long id,
      @PathVariable String decision,
      @Valid @RequestBody MedicalTeamService.Reason d,
      Authentication a) {
    return service.decide(id, decision, d, a.getName());
  }

  @GetMapping("/work")
  public Page<Map<String, Object>> work(
      @RequestParam(required = false) Long teamId,
      @RequestParam(required = false) String employeeStaffCode,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      Authentication a) {
    return service.work(teamId, employeeStaffCode, status, page, size, a.getName());
  }

  @PostMapping("/work")
  public ResponseEntity<List<Map<String, Object>>> assign(
      @Valid @RequestBody MedicalTeamService.WorkRequest d, Authentication a) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.assign(d, a.getName()));
  }

  @GetMapping("/work/{id}")
  public Map<String, Object> detail(
      @PathVariable Long id, @RequestParam(defaultValue = "0") int page, Authentication a) {
    return service.detail(id, page, a.getName());
  }

  @GetMapping("/work/{id}/clinical-case")
  public com.pharmasafe.dto.CaseResponseDto clinical(@PathVariable Long id, Authentication a) {
    return service.clinicalCase(id, a.getName());
  }

  @GetMapping("/work/{id}/laboratory")
  public java.util.List<com.pharmasafe.dto.LabResultDto> laboratory(
      Authentication a, @PathVariable Long id) {
    return workbench.laboratory(a.getName(), id);
  }

  @GetMapping("/cases")
  public Page<Map<String, Object>> cases(
      Authentication a,
      @RequestParam(required = false) Long teamId,
      @RequestParam(defaultValue = "0") int page) {
    return workbench.cases(a.getName(), teamId, page);
  }

  @PostMapping("/teams/{id}/case-allocations")
  public Map<String, Object> allocate(
      Authentication a,
      @PathVariable Long id,
      @Valid @RequestBody com.pharmasafe.medicalteam.MedicalCaseWorkbench.Allocate d) {
    return workbench.allocate(a.getName(), id, d);
  }

  @PostMapping("/work/{id}/assessments")
  public com.pharmasafe.dto.CaseResponseDto assessment(
      Authentication a,
      @PathVariable Long id,
      @Valid @RequestBody com.pharmasafe.medicalteam.MedicalCaseWorkbench.Assess d) {
    return workbench.assess(a.getName(), id, d);
  }

  @PostMapping("/work/{id}/conclusions")
  public Map<String, Object> conclusion(
      Authentication a,
      @PathVariable Long id,
      @Valid @RequestBody com.pharmasafe.medicalteam.MedicalCaseWorkbench.Conclude d) {
    return workbench.conclude(a.getName(), id, d);
  }

  @PostMapping("/work/{id}/decision")
  public com.pharmasafe.dto.CaseResponseDto decision(
      Authentication a,
      @PathVariable Long id,
      @Valid @RequestBody com.pharmasafe.medicalteam.MedicalCaseWorkbench.Decide d) {
    return workbench.decide(a.getName(), id, d);
  }

  @PostMapping("/work/{id}/updates")
  public ResponseEntity<Map<String, Object>> update(
      @PathVariable Long id,
      @Valid @RequestBody MedicalTeamService.UpdateRequest d,
      Authentication a) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.update(id, d, a.getName()));
  }

  @PostMapping("/work/{id}/reopen")
  public Map<String, Object> reopen(
      @PathVariable Long id, @Valid @RequestBody MedicalTeamService.Reason d, Authentication a) {
    return service.reopen(id, d, a.getName());
  }

  @GetMapping("/work/{id}/documents/{documentId}/pdf")
  public ResponseEntity<byte[]> download(
      @PathVariable Long id, @PathVariable String documentId, Authentication a) {
    var d = service.download(id, documentId, a.getName());
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .cacheControl(CacheControl.noStore())
        .header("Pragma", "no-cache")
        .header("X-Content-Type-Options", "nosniff")
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(d.fileName()).build().toString())
        .body(d.bytes());
  }
}
