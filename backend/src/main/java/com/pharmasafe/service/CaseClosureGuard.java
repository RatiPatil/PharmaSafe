package com.pharmasafe.service;
import com.pharmasafe.entity.SafetyCase;
import com.pharmasafe.repository.*;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class CaseClosureGuard {
 private final CaseActionItemRepository actions; private final SafetyReportRepository reports;
 public CaseClosureGuard(CaseActionItemRepository actions,SafetyReportRepository reports) { this.actions=actions;this.reports=reports; }
 public record Readiness(boolean canClose,long openActionItems,long pendingExpeditedReports,List<String> blockers) {}
 public Readiness check(SafetyCase c) {
  long open=actions.countBySafetyCaseIdAndStatusNotIn(c.getId(),List.of("COMPLETED","CANCELLED"));
  long pending=reports.countPendingExpedited(c.getId());List<String> blockers=new ArrayList<>();
  if(!"Locked".equals(c.getWorkflowState())) blockers.add("Route the case to Locked before formal closure.");
  if(open>0) blockers.add("Complete "+open+" open action item(s), including requested follow-up information.");
  if(pending>0) blockers.add("Record submission of "+pending+" pending expedited report(s).");
  return new Readiness(blockers.isEmpty(),open,pending,List.copyOf(blockers));
 }
 public void requireReady(SafetyCase c) { var ready=check(c);if(!ready.canClose())throw new IllegalStateException(String.join(" ",ready.blockers())); }
}
