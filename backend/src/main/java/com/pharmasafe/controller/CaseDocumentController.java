package com.pharmasafe.controller;
import com.pharmasafe.dto.CaseDocumentDto;
import com.pharmasafe.service.CaseDocumentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/cases/{caseId}/documents") @PreAuthorize("isAuthenticated()")
public class CaseDocumentController {
 private final CaseDocumentService service;public CaseDocumentController(CaseDocumentService service){this.service=service;}
 private String role(Authentication a){return a.getAuthorities().iterator().next().getAuthority();}
 @GetMapping public Page<CaseDocumentDto> list(@PathVariable Long caseId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,Authentication a){return service.list(caseId,a.getName(),role(a),PageRequest.of(Math.max(0,page),Math.max(1,Math.min(50,size)),Sort.by(Sort.Order.desc("generatedAt"),Sort.Order.asc("id"))));}
 @PostMapping public ResponseEntity<CaseDocumentDto> create(@PathVariable Long caseId,@Valid @RequestBody CaseDocumentDto.Request request,Authentication a){return ResponseEntity.status(HttpStatus.CREATED).body(service.create(caseId,request,a.getName(),role(a)));}
 @GetMapping("/{documentId}/pdf") public ResponseEntity<byte[]> download(@PathVariable Long caseId,@PathVariable String documentId,Authentication a){var pdf=service.download(caseId,documentId,a.getName(),role(a));return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore()).header("Pragma","no-cache").header("X-Content-Type-Options","nosniff").header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(pdf.fileName()).build().toString()).body(pdf.bytes());}
}
