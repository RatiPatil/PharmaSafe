package com.pharmasafe.validation;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
@ControllerAdvice(basePackages="com.pharmasafe.controller")
public class QueryValidationAdvice {
 @ModelAttribute public void validateQuery(HttpServletRequest request){
  Map<String,String> errors=new LinkedHashMap<>();request.getParameterMap().forEach((key,values)->{
   for(String value:values){if(value.length()>200||value.chars().anyMatch(c->c<32||c==127))errors.put(key,key+": maximum 200 characters without control characters");
    if(Set.of("page","size","assignedUserId").contains(key))try{long n=Long.parseLong(value);if(n<(key.equals("page")?0:1)||key.equals("size")&&n>100||key.equals("page")&&n>1000000)throw new IllegalArgumentException();}catch(Exception e){errors.put(key,key+": enter a supported positive whole number (page may be zero)");}
   }
  });if(!errors.isEmpty())throw new InputValidationException(errors);
 }
}
