package com.pharmasafe.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharmasafe.education.*;
import com.pharmasafe.service.*;
import com.pharmasafe.validation.InputRules;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/education")
public class EducationController {
  private final EducationService service;
  private final AccessRateLimiter limiter;
  private final InputRules rules;
  private final ObjectMapper json;

  public EducationController(
      EducationService s, AccessRateLimiter l, InputRules r, ObjectMapper j) {
    service = s;
    limiter = l;
    rules = r;
    json = j;
  }

  @GetMapping("/catalog")
  public Map<String, Object> catalog() {
    return service.catalog();
  }

  private void limit(HttpServletRequest r, String action, int max) {
    limiter.check("education-" + action + ":" + UserAccessService.hash(r.getRemoteAddr()), max);
  }

  @PostMapping("/student-id")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> reserve(
      @Valid @RequestBody EducationService.Reserve d, HttpServletRequest r) {
    limit(r, "id", 100);
    return service.reserve(d);
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> register(
      @Valid @RequestBody EducationService.Register d, HttpServletRequest r) {
    limit(r, "register", 60);
    return service.register(d);
  }

  @GetMapping("/overview")
  public Map<String, Object> overview(
      Authentication a, @Valid @ModelAttribute EducationService.Filter f) {
    return service.overview(a.getName(), f);
  }

  @GetMapping("/colleges")
  public List<Map<String, Object>> colleges(
      Authentication a, @RequestParam(required = false) String domain) {
    return service.colleges(a.getName(), domain);
  }

  @PostMapping("/colleges")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> createCollege(
      Authentication a, @Valid @RequestBody EducationService.CollegeInput d) {
    return service.createCollege(a.getName(), d);
  }

  @PutMapping("/colleges/{id}")
  public Map<String, Object> collegeState(
      Authentication a,
      @PathVariable Long id,
      @Valid @RequestBody EducationService.CollegeState d) {
    return service.collegeState(a.getName(), id, d);
  }

  @GetMapping("/administrators")
  public List<Map<String, Object>> admins(Authentication a) {
    return service.administrators(a.getName());
  }

  @PostMapping("/administrators")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> admin(
      Authentication a, @Valid @RequestBody EducationService.AdminInput d) {
    return service.createAdmin(a.getName(), d);
  }

  @PutMapping("/administrators/{kind}/{id}")
  public Map<String, String> adminState(
      Authentication a,
      @PathVariable String kind,
      @PathVariable Long id,
      @Valid @RequestBody EducationService.State d) {
    service.adminState(a.getName(), kind, id, d);
    return Map.of("message", "Administrator access updated");
  }

  @GetMapping("/students")
  public Map<String, Object> students(
      Authentication a,
      @Valid @ModelAttribute EducationService.Filter f,
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "0") int page) {
    return service.students(a.getName(), f, search, page);
  }

  @GetMapping("/requests")
  public Map<String, Object> requests(
      Authentication a,
      @RequestParam(required = false) String domain,
      @RequestParam(required = false) Long collegeId,
      @RequestParam(defaultValue = "PENDING") String status,
      @RequestParam(defaultValue = "0") int page) {
    return service.requests(a.getName(), domain, collegeId, status, page);
  }

  @PostMapping("/requests/{id}/decision")
  public Map<String, Object> decide(
      Authentication a, @PathVariable Long id, @Valid @RequestBody EducationService.Decision d) {
    return service.decide(a.getName(), id, d);
  }

  @GetMapping("/tasks")
  public Map<String, Object> tasks(
      Authentication a,
      @Valid @ModelAttribute EducationService.Filter f,
      @RequestParam(defaultValue = "0") int page) {
    return service.tasks(a.getName(), f, page);
  }

  @PostMapping("/tasks")
  @ResponseStatus(HttpStatus.CREATED)
  public List<Map<String, Object>> assign(
      Authentication a, @Valid @RequestBody EducationService.Assign d) {
    return service.assign(a.getName(), d);
  }

  @GetMapping("/tasks/{id}")
  public Map<String, Object> detail(
      Authentication a, @PathVariable Long id, @RequestParam(defaultValue = "0") int page) {
    return service.detail(a.getName(), id, page);
  }

  @PostMapping(value = "/tasks/{id}/submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> submit(
      Authentication a,
      @PathVariable Long id,
      @Valid @RequestPart("data") EducationService.Submit d,
      @RequestPart(value = "file", required = false) MultipartFile file) {
    rules.validate(json.valueToTree(d), "/api/education/submissions");
    return service.submit(a.getName(), id, d, file);
  }

  @PostMapping("/tasks/{id}/review")
  public Map<String, Object> review(
      Authentication a, @PathVariable Long id, @Valid @RequestBody EducationService.Review d) {
    return service.review(a.getName(), id, d);
  }

  @GetMapping("/tasks/{task}/documents/{id}/pdf")
  public ResponseEntity<byte[]> document(
      Authentication a, @PathVariable Long task, @PathVariable Long id) {
    return download(service.document(a.getName(), task, id));
  }

  @GetMapping("/reports")
  public Map<String, Object> reports(
      Authentication a,
      @Valid @ModelAttribute EducationService.Filter f,
      @RequestParam(defaultValue = "0") int page) {
    return service.reports(a.getName(), f, page);
  }

  @PostMapping("/reports")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> report(
      Authentication a, @Valid @RequestBody EducationService.Filter f) {
    return service.report(a.getName(), f);
  }

  @GetMapping("/reports/{id}/pdf")
  public ResponseEntity<byte[]> pdf(Authentication a, @PathVariable Long id) {
    return download(service.reportPdf(a.getName(), id));
  }

  @PostMapping("/students")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> addStudent(
      Authentication a, @Valid @RequestBody EducationService.StudentCreate d) {
    return service.addStudent(a.getName(), d);
  }

  @GetMapping("/students/{id}")
  public Map<String, Object> studentProfile(
      Authentication a, @PathVariable Long id, @RequestParam(required = false) String domain) {
    return service.studentProfile(a.getName(), id, domain);
  }

  @PutMapping("/students/{id}")
  public Map<String, Object> editStudent(
      Authentication a, @PathVariable Long id, @Valid @RequestBody EducationService.StudentEdit d) {
    return service.editStudent(a.getName(), id, d);
  }

  @PutMapping("/tasks/{id}")
  public Map<String, Object> editTask(
      Authentication a, @PathVariable Long id, @Valid @RequestBody EducationService.WorkEdit d) {
    return service.editTask(a.getName(), id, d);
  }

  @GetMapping("/orders")
  public List<Map<String, Object>> orders(
      Authentication a, @Valid @ModelAttribute EducationService.Filter f) {
    return service.orders(a.getName(), f);
  }

  @PostMapping("/orders")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> addOrder(
      Authentication a, @Valid @RequestBody EducationService.OrderInput d) {
    return service.addOrder(a.getName(), d);
  }

  @PutMapping("/orders/{id}")
  public Map<String, Object> editOrder(
      Authentication a, @PathVariable Long id, @Valid @RequestBody EducationService.WorkEdit d) {
    return service.editOrder(a.getName(), id, d);
  }

  @GetMapping("/profile")
  public Map<String, Object> profile(Authentication a) {
    return service.myProfile(a.getName());
  }

  @GetMapping("/preferences")
  public EducationService.Preferences preferences(Authentication a) {
    return service.preferences(a.getName());
  }

  @PutMapping("/preferences")
  public EducationService.Preferences preferences(
      Authentication a, @RequestBody EducationService.Preferences d) {
    return service.preferences(a.getName(), d);
  }

  @GetMapping("/student-access")
  public Map<String, Object> pendingAccess(
      Authentication a, @RequestParam(defaultValue = "0") int page) {
    return service.pendingStudentAccess(a.getName(), page);
  }

  @PostMapping("/student-access/{id}/decision")
  public Map<String, Object> decideAccess(
      Authentication a,
      @PathVariable Long id,
      @Valid @RequestBody EducationService.StudentAccessDecision d) {
    return service.decideStudentAccess(a.getName(), id, d);
  }

  private ResponseEntity<byte[]> download(EducationService.Download d) {
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(d.fileName()).build().toString())
        .body(d.bytes());
  }
}
