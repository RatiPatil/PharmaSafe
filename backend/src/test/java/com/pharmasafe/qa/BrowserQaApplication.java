package com.pharmasafe.qa;

import com.pharmasafe.PharmaSafeApplication;
import com.pharmasafe.service.AccessRateLimiter;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Browser-only H2 adapter. This test source is never packaged in production. */
public class BrowserQaApplication {
  public static void main(String[] args) {
    new SpringApplication(PharmaSafeApplication.class, H2RateLimits.class).run(args);
  }

  @TestConfiguration(proxyBeanMethods = false)
  public static class H2RateLimits {
    @Bean
    @Primary
    AccessRateLimiter browserRateLimiter(JdbcTemplate jdbc, Environment env) {
      if (!env.getProperty("spring.datasource.url", "").startsWith("jdbc:h2:mem:"))
        throw new IllegalStateException("Browser QA requires an isolated in-memory H2 database");
      return new AccessRateLimiter(jdbc) {
        final ConcurrentHashMap<String, long[]> buckets = new ConcurrentHashMap<>();

        @Override
        public void check(String bucket, int limit) {
          long now = System.currentTimeMillis();
          long[] value =
              buckets.compute(
                  bucket,
                  (key, old) ->
                      old == null || old[0] < now - 3600000
                          ? new long[] {now, 1}
                          : new long[] {old[0], old[1] + 1});
          if (value[1] > limit)
            throw new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Try again later.");
        }
      };
    }
  }
}
