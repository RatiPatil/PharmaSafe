package com.pharmasafe.validation;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import java.lang.reflect.Type;
/** Validates every JSON write body before a controller can change persistent data. */
@ControllerAdvice(basePackages="com.pharmasafe.controller")
public class InputValidationAdvice extends RequestBodyAdviceAdapter {
 private final ObjectMapper mapper;private final InputRules rules;
 public InputValidationAdvice(ObjectMapper mapper,InputRules rules){this.mapper=mapper;this.rules=rules;}
 @Override public boolean supports(MethodParameter p,Type t,Class<? extends HttpMessageConverter<?>> c){return org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter.class.isAssignableFrom(c);}
 @Override public Object afterBodyRead(Object body,HttpInputMessage input,MethodParameter parameter,Type type,Class<? extends HttpMessageConverter<?>> converter){
  var attributes=org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
  if(attributes instanceof org.springframework.web.context.request.ServletRequestAttributes request)rules.validate(mapper.valueToTree(body),request.getRequest().getRequestURI());
  return body;
 }
}
