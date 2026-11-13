package com.pharmasafe.validation;
import java.util.Map;
public class InputValidationException extends IllegalArgumentException {
 private final Map<String,String> errors;
 public InputValidationException(Map<String,String> errors){super("Please correct the highlighted fields. "+errors.values().stream().findFirst().orElse("Invalid input"));this.errors=Map.copyOf(errors);}
 public Map<String,String> getErrors(){return errors;}
}
