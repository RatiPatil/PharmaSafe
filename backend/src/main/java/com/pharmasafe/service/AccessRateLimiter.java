package com.pharmasafe.service;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service public class AccessRateLimiter {
 private final JdbcTemplate jdbc;
 public AccessRateLimiter(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @Transactional(propagation=Propagation.REQUIRES_NEW,noRollbackFor=ResponseStatusException.class)
 public void check(String bucket,int limit){
  Integer n=jdbc.queryForObject("INSERT INTO access_rate_limits(bucket,window_start,attempts) VALUES (?,CURRENT_TIMESTAMP,1) ON CONFLICT(bucket) DO UPDATE SET attempts=CASE WHEN access_rate_limits.window_start < CURRENT_TIMESTAMP - INTERVAL '1 hour' THEN 1 ELSE access_rate_limits.attempts+1 END, window_start=CASE WHEN access_rate_limits.window_start < CURRENT_TIMESTAMP - INTERVAL '1 hour' THEN CURRENT_TIMESTAMP ELSE access_rate_limits.window_start END RETURNING attempts",Integer.class,bucket);
  if(n!=null&&n>limit)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Too many requests. Try again later.");
 }
}
