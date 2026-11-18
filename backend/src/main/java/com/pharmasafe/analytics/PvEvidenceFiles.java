package com.pharmasafe.analytics;

import com.fasterxml.jackson.databind.*;
import com.pharmasafe.service.*;
import org.springframework.stereotype.Component;

@Component
public class PvEvidenceFiles {
  private final ObjectMapper json;
  private final CasePdfRenderer renderer;

  public PvEvidenceFiles(ObjectMapper j, CasePdfRenderer r) {
    json = j;
    renderer = r;
  }

  public byte[] render(JsonNode snapshot) {
    try {
      byte[] source = json.writeValueAsBytes(snapshot);
      return renderer.render(snapshot, source);
    } catch (java.io.IOException e) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
          "PDF generation failed; no evidence was saved",
          e);
    }
  }

  public String hash(byte[] bytes) {
    try {
      return java.util.HexFormat.of()
          .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public CaseDocumentService.Download download(String name, byte[] bytes, String sha) {
    if (!hash(bytes).equals(sha))
      throw new IllegalStateException("PDF integrity verification failed");
    return new CaseDocumentService.Download(name, bytes, sha);
  }
}
