package com.pharmasafe.controller;
import com.pharmasafe.dto.AccessRegistrationDto;
import com.pharmasafe.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import java.time.LocalDate;
import java.util.*;
@RestController
public class UserAccessController {
 private final UserAccessService access;private final AccessRoleCatalog catalog;private final AccessRateLimiter limiter;
 public UserAccessController(UserAccessService access,AccessRoleCatalog catalog,AccessRateLimiter limiter){this.access=access;this.catalog=catalog;this.limiter=limiter;}
 public record RoleSelection(@NotBlank @Size(max=32) String role){}
 public record Identifier(@NotBlank @Size(max=254) String identifier){}
 public record Reset(@NotBlank @Size(max=256) String token,@NotBlank @Size(max=72) String password,@NotBlank @Size(max=72) String confirmPassword){}
 public record Decision(@NotBlank @Size(max=2000) String reason){}
 private void limit(HttpServletRequest request,String action,int maximum){limiter.check(action+":"+UserAccessService.hash(request.getRemoteAddr()),maximum);}
 @GetMapping("/api/auth/access-roles") public List<Map<String,Object>> roles(){return catalog.roles();}
 @PostMapping("/api/auth/role-selection") public Map<String,String> select(@Valid @RequestBody RoleSelection body,HttpServletRequest request){limit(request,"selection",60);return Map.of("selectionToken",catalog.issue(body.role()));}
 @PostMapping("/api/auth/register") @ResponseStatus(HttpStatus.CREATED)
 public Map<String,Object> register(@Valid @RequestBody AccessRegistrationDto body,HttpServletRequest request){limit(request,"register",10);return access.register(body);}
 @PostMapping("/api/auth/forgot-password") public Map<String,String> forgot(@Valid @RequestBody Identifier body,HttpServletRequest request){limit(request,"forgot",10);access.forgot(body.identifier());return Map.of("message","If an eligible account exists, your request will be reviewed by an administrator.");}
 @PostMapping("/api/auth/reset-password") public Map<String,String> reset(@Valid @RequestBody Reset body,HttpServletRequest request){limit(request,"reset",20);access.reset(body.token(),body.password(),body.confirmPassword());return Map.of("message","Password changed. Sign in with your new password.");}
 @GetMapping("/api/admin/registration-requests") @PreAuthorize("hasRole('ADMIN')")
 public Page<Map<String,Object>> registrations(@RequestParam(defaultValue="") String role,@RequestParam(defaultValue="") String status,
 @RequestParam(defaultValue="") String department,@RequestParam(defaultValue="") String organization,@RequestParam(defaultValue="") String search,
 @RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,
 @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="createdAt") String sort,@RequestParam(defaultValue="desc") String direction){return access.registrations(role,status,department,organization,search,from,to,page,size,sort,direction);}
 @GetMapping("/api/admin/registration-requests/{id}") @PreAuthorize("hasRole('ADMIN')") public Map<String,Object> details(@PathVariable Long id){return access.details(id);}
 @PutMapping("/api/admin/registration-requests/{id}/{decision:approve|reject}") @PreAuthorize("hasRole('ADMIN')") public Map<String,Object> registrationDecision(@PathVariable Long id,@PathVariable String decision,@Valid @RequestBody Decision body,Authentication auth){return access.decideRegistration(id,decision,body.reason(),auth.getName());}
 @PutMapping("/api/admin/users/{id}/{action:suspend|activate}") @PreAuthorize("hasRole('ADMIN')") public Map<String,Object> status(@PathVariable Long id,@PathVariable String action,@Valid @RequestBody Decision body,Authentication auth){return access.accountStatus(id,action,body.reason(),auth.getName());}
 @GetMapping("/api/admin/password-reset-requests") @PreAuthorize("hasRole('ADMIN')") public Page<Map<String,Object>> resets(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="") String status){return access.resetRequests(page,size,status);}
 @PutMapping("/api/admin/password-reset-requests/{id}/{decision:approve|reject}") @PreAuthorize("hasRole('ADMIN')") public Map<String,Object> resetDecision(@PathVariable Long id,@PathVariable String decision,@Valid @RequestBody Decision body,Authentication auth){return access.decideReset(id,decision,body.reason(),auth.getName());}
 @GetMapping("/api/admin/access-users") @PreAuthorize("hasRole('ADMIN')")
 public Page<Map<String,Object>> accounts(@RequestParam(defaultValue="") String role,@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="") String department,@RequestParam(defaultValue="") String organization,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return access.accounts(role,status,department,organization,search,page,size);}
 @GetMapping("/api/admin/access-users/{id}") @PreAuthorize("hasRole('ADMIN')")
 public Map<String,Object> accountDetails(@PathVariable Long id){return access.accountDetails(id);}
}
