package com.pharmasafe.controller;

import com.pharmasafe.processing.ProcessingTeamService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/processing-teams")
public class ProcessingTeamController {
  private final ProcessingTeamService service;
  private final com.pharmasafe.processing.ProcessingCaseWorkbench workbench;

  public ProcessingTeamController(
      ProcessingTeamService s, com.pharmasafe.processing.ProcessingCaseWorkbench wb) {
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
      @Valid @RequestBody ProcessingTeamService.CreateTeam d, Authentication a) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.createTeam(d, a.getName()));
  }

  @PutMapping("/teams/{id}")
  public Map<String, Object> state(
      @PathVariable Long id,
      @Valid @RequestBody ProcessingTeamService.TeamState d,
      Authentication a) {
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
      @Valid @RequestBody ProcessingTeamService.MemberRequest d,
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
      @Valid @RequestBody ProcessingTeamService.Reason d,
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
      @Valid @RequestBody ProcessingTeamService.WorkRequest d, Authentication a) {
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

  @PostMapping("/work/{id}/clinical-case")
  public ResponseEntity<com.pharmasafe.dto.CaseResponseDto> createClinicalCase(
      @PathVariable Long id,
      @Valid @RequestBody com.pharmasafe.dto.CaseRequestDto d,
      Authentication a,
      jakarta.servlet.http.HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            workbench.create(
                id, d, a.getName(), request.getRemoteAddr(), request.getHeader("User-Agent")));
  }

  @PostMapping("/work/{id}/clinical-case/link")
  public com.pharmasafe.dto.CaseResponseDto linkClinicalCase(
      @PathVariable Long id,
      @Valid @RequestBody com.pharmasafe.processing.ProcessingCaseWorkbench.LinkRequest d,
      Authentication a) {
    return workbench.link(id, d, a.getName());
  }

  @PostMapping("/work/{id}/processing-evidence")
  public Map<String, Object> evidence(
      @PathVariable Long id,
      @Valid @RequestBody com.pharmasafe.processing.ProcessingCaseWorkbench.EvidenceRequest d,
      Authentication a) {
    return workbench.record(id, d, a.getName());
  }

  @PostMapping("/work/{id}/medical-handoff")
  public com.pharmasafe.dto.CaseResponseDto medicalHandoff(
      @PathVariable Long id,
      @Valid @RequestBody com.pharmasafe.processing.ProcessingCaseWorkbench.HandoffRequest d,
      Authentication a) {
    return workbench.handoff(id, d, a.getName());
  }

  @PostMapping("/work/{id}/updates")
  public ResponseEntity<Map<String, Object>> update(
      @PathVariable Long id,
      @Valid @RequestBody ProcessingTeamService.UpdateRequest d,
      Authentication a) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.update(id, d, a.getName()));
  }

  @PostMapping("/work/{id}/reopen")
  public Map<String, Object> reopen(
      @PathVariable Long id, @Valid @RequestBody ProcessingTeamService.Reason d, Authentication a) {
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
