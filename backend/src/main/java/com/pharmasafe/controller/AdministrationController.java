package com.pharmasafe.controller;

import com.pharmasafe.administration.*;
import com.pharmasafe.service.UserAccessService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/administration")
@PreAuthorize("hasAnyRole('ADMIN','SCOPED_ADMIN')")
public class AdministrationController {
  private final AdministrationService admin;
  private final UserAccessService access;

  public AdministrationController(AdministrationService admin, UserAccessService access) {
    this.admin = admin;
    this.access = access;
  }

  public record Decision(@NotBlank @Size(max = 2000) String reason) {}

  public record Provision(
      @NotBlank @Size(max = 64) String username,
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(max = 128) String fullName,
      @NotBlank String password,
      @NotBlank @Size(max = 2000) String reason,
      @NotNull @PositiveOrZero Long expectedVersion) {}

  public record Assign(
      @NotNull @Positive Long userId,
      @NotBlank @Size(max = 2000) String reason,
      @NotNull @PositiveOrZero Long expectedVersion) {}

  public record Release(
      @NotBlank @Size(max = 2000) String reason, @NotNull @PositiveOrZero Long expectedVersion) {}

  @GetMapping("/overview")
  public Map<String, Object> overview(
      Authentication a, @RequestParam(defaultValue = "") String role) {
    return admin.overview(a.getName(), role);
  }

  @GetMapping("/users")
  public Page<Map<String, Object>> users(
      Authentication a,
      @RequestParam(defaultValue = "") String role,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "") String department,
      @RequestParam(defaultValue = "") String organization,
      @RequestParam(defaultValue = "") String search,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "username") String sort,
      @RequestParam(defaultValue = "asc") String direction) {
    return admin.accounts(
        a.getName(),
        role,
        status,
        department,
        organization,
        search,
        from,
        to,
        page,
        size,
        sort,
        direction);
  }

  @GetMapping("/users/{id}")
  public Map<String, Object> details(Authentication a, @PathVariable Long id) {
    return admin.details(a.getName(), id, false);
  }

  @PutMapping("/users/{id}/{action:suspend|activate}")
  public Map<String, Object> status(
      Authentication a,
      @PathVariable Long id,
      @PathVariable String action,
      @Valid @RequestBody Decision d) {
    return access.accountStatus(id, action, d.reason(), a.getName());
  }

  @GetMapping("/registrations")
  public Page<Map<String, Object>> registrations(
      Authentication a,
      @RequestParam(defaultValue = "") String role,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "") String department,
      @RequestParam(defaultValue = "") String organization,
      @RequestParam(defaultValue = "") String search,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(required = false) Long administratorId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "createdAt") String sort,
      @RequestParam(defaultValue = "desc") String direction) {
    return admin.registrationList(
        a.getName(),
        role,
        status,
        department,
        organization,
        search,
        from,
        to,
        administratorId,
        page,
        size,
        sort,
        direction);
  }

  @GetMapping("/registrations/{id}")
  public Map<String, Object> registration(Authentication a, @PathVariable Long id) {
    return admin.details(a.getName(), id, true);
  }

  @PutMapping("/registrations/{id}/{decision:approve|reject}")
  public Map<String, Object> registrationDecision(
      Authentication a,
      @PathVariable Long id,
      @PathVariable String decision,
      @Valid @RequestBody Decision d) {
    return access.decideRegistration(id, decision, d.reason(), a.getName());
  }

  @GetMapping("/password-resets")
  public Page<Map<String, Object>> resets(
      Authentication a,
      @RequestParam(defaultValue = "") String role,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return admin.resetRequests(a.getName(), role, status, page, size);
  }

  @PutMapping("/password-resets/{id}/{decision:approve|reject}")
  public Map<String, Object> resetDecision(
      Authentication a,
      @PathVariable Long id,
      @PathVariable String decision,
      @Valid @RequestBody Decision d) {
    return access.decideReset(id, decision, d.reason(), a.getName());
  }

  @GetMapping("/audit")
  public Page<AdministrativeEvent> audit(
      Authentication a,
      @RequestParam(defaultValue = "") String role,
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return admin.history(a.getName(), role, search, page, size);
  }

  @GetMapping("/work")
  public Page<Map<String, Object>> work(
      Authentication a,
      @RequestParam(defaultValue = "") String role,
      @RequestParam(required = false) Long userId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return admin.work(a.getName(), role, userId, page, size);
  }

  @GetMapping("/report-work")
  public Page<Map<String, Object>> reportWork(
      Authentication a,
      @RequestParam(defaultValue = "") String role,
      @RequestParam(required = false) Long userId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return admin.reportWork(a.getName(), role, userId, page, size);
  }

  @GetMapping("/eligible-administrators")
  @PreAuthorize("hasRole('ADMIN')")
  public Page<Map<String, Object>> eligible(
      Authentication a,
      @RequestParam String role,
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return admin.eligible(a.getName(), role, search, page, size);
  }

  @GetMapping("/activity")
  public Page<Map<String, Object>> activity(
      Authentication a,
      @RequestParam(defaultValue = "") String role,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return admin.activity(a.getName(), role, page, size);
  }

  @PostMapping("/assignments/{role}")
  @PreAuthorize("hasRole('ADMIN')")
  public Map<String, Object> provision(
      Authentication a, @PathVariable String role, @Valid @RequestBody Provision d) {
    return admin.provision(
        a.getName(),
        role,
        d.username(),
        d.email(),
        d.fullName(),
        d.password(),
        d.reason(),
        d.expectedVersion());
  }

  @PutMapping("/assignments/{role}")
  @PreAuthorize("hasRole('ADMIN')")
  public Map<String, Object> assign(
      Authentication a, @PathVariable String role, @Valid @RequestBody Assign d) {
    return admin.assignExisting(a.getName(), role, d.userId(), d.reason(), d.expectedVersion());
  }

  @PostMapping("/assignments/{role}/release")
  @PreAuthorize("hasRole('ADMIN')")
  public Map<String, Object> release(
      Authentication a, @PathVariable String role, @Valid @RequestBody Release d) {
    return admin.release(a.getName(), role, d.reason(), d.expectedVersion());
  }
}
