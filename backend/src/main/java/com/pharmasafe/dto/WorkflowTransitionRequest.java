package com.pharmasafe.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;
public record WorkflowTransitionRequest(@NotBlank String targetState, @Size(max=10000) String comments,
                                        Set<String> qcChecklist) {}
