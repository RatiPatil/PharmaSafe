package com.pharmasafe.education;

import com.pharmasafe.entity.User;
import jakarta.persistence.*;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class EducationAccess {
  @PersistenceContext private EntityManager em;
  public static final Set<String> ROLES =
      Set.of("ROLE_DOMAIN_ADMIN", "ROLE_COLLEGE_ADMIN", "ROLE_STUDENT");

  public boolean enabled(User u) {
    return switch (u.getRole()) {
      case "ROLE_DOMAIN_ADMIN" ->
          em.createQuery(
                      "select count(g) from EduDomainAdmin g where g.user.id=:u and g.active=true",
                      Long.class)
                  .setParameter("u", u.getId())
                  .getSingleResult()
              > 0;
      case "ROLE_COLLEGE_ADMIN" ->
          em.createQuery(
                      "select count(g) from EduCollegeAdmin g where g.user.id=:u and g.active=true"
                          + " and g.college.active=true",
                      Long.class)
                  .setParameter("u", u.getId())
                  .getSingleResult()
              > 0;
      case "ROLE_STUDENT" ->
          em.createQuery(
                      "select count(s) from EduStudent s where s.user.id=:u and"
                          + " s.college.active=true",
                      Long.class)
                  .setParameter("u", u.getId())
                  .getSingleResult()
              > 0;
      default -> true;
    };
  }
}
