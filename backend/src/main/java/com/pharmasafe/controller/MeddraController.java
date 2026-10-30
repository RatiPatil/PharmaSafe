package com.pharmasafe.controller;

import com.pharmasafe.entity.MeddraTerm;
import com.pharmasafe.repository.MeddraTermRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/meddra")
@PreAuthorize("isAuthenticated()")
public class MeddraController {

    private final MeddraTermRepository meddraTermRepository;

    public MeddraController(MeddraTermRepository meddraTermRepository) {
        this.meddraTermRepository = meddraTermRepository;
    }

    @GetMapping("/search")
    public ResponseEntity<Page<MeddraTerm>> searchTerms(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String soc,
            @RequestParam(required = false) String level,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int boundedSize = Math.max(1, Math.min(size, 100));
        Pageable pageable = PageRequest.of(Math.max(0, page), boundedSize, Sort.by("term").ascending());
        Page<MeddraTerm> results = meddraTermRepository.searchTerms(query, soc, level, pageable);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/socs")
    public ResponseEntity<List<String>> getDistinctSocs() {
        List<String> socs = meddraTermRepository.findDistinctSocs();
        return ResponseEntity.ok(socs);
    }

    @GetMapping("/{code}")
    public ResponseEntity<MeddraTerm> getTermByCode(@PathVariable String code) {
        return meddraTermRepository.findByCode(code)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
