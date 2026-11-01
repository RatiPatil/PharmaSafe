package com.pharmasafe.controller;

import com.pharmasafe.dto.CaseSummaryDto;
import com.pharmasafe.entity.*;
import com.pharmasafe.repository.SafetyCaseRepository;
import com.pharmasafe.service.WorkspaceQueueService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import jakarta.persistence.criteria.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/cases/duplicate-check")
@PreAuthorize("hasAnyRole('ADMIN','SAFETY_MANAGER','CASE_PROCESSOR','USER')")
public class DuplicateCheckController {
 private final SafetyCaseRepository cases; private final WorkspaceQueueService queue;
 public DuplicateCheckController(SafetyCaseRepository cases,WorkspaceQueueService queue){this.cases=cases;this.queue=queue;}
 public record Criteria(@Size(max=255) String patientIdentifier,@Size(max=255) String productName,@Size(max=1000) String eventTerm,Long excludeCaseId){}
 private String norm(String s){return s==null?"":s.trim().toLowerCase(Locale.ROOT);}
 @PostMapping @Transactional(readOnly=true)
 public Page<CaseSummaryDto> check(@Valid @RequestBody Criteria input,Authentication auth){
  String patient=norm(input.patientIdentifier()),product=norm(input.productName()),event=norm(input.eventTerm());
  if(patient.isEmpty()&&(product.isEmpty()||event.isEmpty()))throw new IllegalArgumentException("Enter a patient identifier or both a product and event before checking");
  Specification<SafetyCase> matches=(root,q,cb)->{
   List<Predicate> alternatives=new ArrayList<>();
   if(!patient.isEmpty()) {
    var sq=q.subquery(Long.class);var p=sq.from(CasePatient.class);
    sq.select(p.get("safetyCase").get("id")).where(cb.equal(p.get("safetyCase").get("id"),root.get("id")),cb.equal(cb.lower(cb.trim(p.get("patientIdentifier"))),patient));alternatives.add(cb.exists(sq));
   }
   if(!product.isEmpty()&&!event.isEmpty()) {
    var ps=q.subquery(Long.class);var p=ps.from(CaseProduct.class);ps.select(p.get("safetyCase").get("id")).where(cb.equal(p.get("safetyCase").get("id"),root.get("id")),cb.equal(cb.lower(cb.trim(p.get("productName"))),product));
    var es=q.subquery(Long.class);var e=es.from(CaseEvent.class);es.select(e.get("safetyCase").get("id")).where(cb.equal(e.get("safetyCase").get("id"),root.get("id")),cb.equal(cb.lower(cb.trim(e.get("eventVerbatim"))),event));
    alternatives.add(cb.and(cb.exists(ps),cb.exists(es)));
   }
   Predicate result=cb.or(alternatives.toArray(Predicate[]::new));
   return input.excludeCaseId()==null?result:cb.and(result,cb.notEqual(root.get("id"),input.excludeCaseId()));
  };
  return cases.findAll(queue.scope(auth,null,null,null,null,null,false).and(matches),PageRequest.of(0,20,Sort.by(Sort.Direction.DESC,"updatedAt").and(Sort.by("id")))).map(CaseSummaryDto::fromEntity);
 }
}
