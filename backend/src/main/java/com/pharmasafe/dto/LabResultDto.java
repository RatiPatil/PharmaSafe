package com.pharmasafe.dto;
import com.pharmasafe.entity.CaseLabResult;
import jakarta.validation.constraints.*;
import java.time.*;
import java.math.BigDecimal;
public record LabResultDto(Long id,String testName,LocalDate testDate,String resultText,String unit,BigDecimal normalLow,BigDecimal normalHigh,String interpretation,String notes,String recordedBy,Instant recordedAt,String voidReason,String voidedBy,Instant voidedAt) {
 public static LabResultDto from(CaseLabResult v){return new LabResultDto(v.getId(),v.getTestName(),v.getTestDate(),v.getResultText(),v.getUnit(),v.getNormalLow(),v.getNormalHigh(),v.getInterpretation(),v.getNotes(),v.getRecordedBy(),v.getRecordedAt(),v.getVoidReason(),v.getVoidedBy(),v.getVoidedAt());}
 public record Create(@NotNull Long expectedRowVersion,@NotBlank @Size(max=200) String testName,@NotNull @PastOrPresent LocalDate testDate,@NotBlank @Size(max=1000) String resultText,@Size(max=80) String unit,@Digits(integer=12,fraction=6) BigDecimal normalLow,@Digits(integer=12,fraction=6) BigDecimal normalHigh,@NotNull @Pattern(regexp="UNKNOWN|NORMAL|ABNORMAL|HIGH|LOW") String interpretation,@Size(max=4000) String notes){}
 public record Void(@NotNull Long expectedRowVersion,@NotBlank @Size(max=1000) String reason){}
}
