package com.pharmasafe.controller;
import com.pharmasafe.entity.User;
import com.pharmasafe.repository.UserRepository;
import com.pharmasafe.service.AuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
@RestController @RequestMapping("/api/profile") @PreAuthorize("isAuthenticated()")
public class ProfileController {
 private final UserRepository users;private final AuditService audit;
 public ProfileController(UserRepository users,AuditService audit){this.users=users;this.audit=audit;}
 public record Profile(Long id,String username,String fullName,String department,String role,long accountRowVersion,Instant lastSuccessfulLoginAt,Instant createdAt){}
 public record Update(@NotBlank @Size(max=128) String fullName,@NotBlank @Size(max=64) String department,@NotNull Long expectedAccountRowVersion){}
 private User current(Authentication a){return users.findByUsername(a.getName()).filter(User::isEnabled).orElseThrow(()->new AccessDeniedException("Account unavailable"));}
 private Profile response(User u){return new Profile(u.getId(),u.getUsername(),u.getFullName(),u.getDepartment(),u.getRole(),u.getAccountRowVersion(),u.getLastSuccessfulLoginAt(),u.getCreatedAt());}
 @GetMapping @Transactional(readOnly=true) public Profile get(Authentication a){return response(current(a));}
 @PatchMapping @Transactional public Profile update(@Valid @RequestBody Update request,Authentication a){User u=current(a);if(request.expectedAccountRowVersion()!=u.getAccountRowVersion())throw new org.springframework.orm.ObjectOptimisticLockingFailureException(User.class,u.getId());u.setFullName(request.fullName().trim());u.setDepartment(request.department().trim());users.saveAndFlush(u);audit.logAccountEvent("OWN_PROFILE_UPDATED",u.getUsername(),"User updated their display name and department");return response(u);}
}
