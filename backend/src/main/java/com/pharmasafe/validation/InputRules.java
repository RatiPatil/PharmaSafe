package com.pharmasafe.validation;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.regex.Pattern;
import java.math.BigDecimal;
import java.time.LocalDate;
@Component
public class InputRules {
 private final JsonNode config;
 public InputRules(ObjectMapper mapper){try(var in=getClass().getResourceAsStream("/validation/input-rules.json")){config=mapper.readTree(in);}catch(Exception e){throw new IllegalStateException("Input validation configuration unavailable",e);}}
 public JsonNode registrationChoices(){return config.path("registrationChoices");}
 public String context(String path){if(path.startsWith("/api/pv-analyses"))return "pv";if(path.startsWith("/api/processing-teams"))return "processing";if(path.equals("/api/auth/register"))return "registration";if(path.startsWith("/api/admin"))return "admin";if(path.startsWith("/api/reports"))return "report";if(path.startsWith("/api/auth"))return "auth";if(path.startsWith("/api/profile"))return "profile";return "case";}
 public void validate(JsonNode value,String path){Map<String,String> errors=new LinkedHashMap<>();walk(value,"",context(path),errors,0);if(!errors.isEmpty())throw new InputValidationException(errors);}
 private void fail(Map<String,String> errors,String path,String reason){if(errors.size()<30)errors.put(path,path.replaceAll("([a-z])([A-Z])","$1 $2")+": "+reason);}
 private JsonNode rule(String context,String path,String key){String normalized=path.replaceAll("\\[\\d+\\]","[]");JsonNode exact=config.path("overrides").path(context+"."+normalized);if(!exact.isMissingNode())return exact;
  if(context.equals("registration")&&path.startsWith("details."))return config.path("overrides").path("registration.details.*");return config.path("fields").path(key);}
 private void walk(JsonNode node,String path,String context,Map<String,String> errors,int depth){
  if(node==null||node.isNull())return;if(depth>16){fail(errors,path,"input structure is too deeply nested");return;}
  if(node.isObject()){
   node.fields().forEachRemaining(e->walk(e.getValue(),path.isEmpty()?e.getKey():path+"."+e.getKey(),context,errors,depth+1));
   for(String[] pair:List.of(new String[]{"initialReceiptDate","latestReceivedDate"},new String[]{"startDate","stopDate"},new String[]{"startDate","endDate"},new String[]{"onsetDate","stopDate"},new String[]{"periodStart","periodEnd"})){
    String a=node.path(pair[0]).asText(""),b=node.path(pair[1]).asText("");if(a.matches("\\d{4}-\\d{2}-\\d{2}")&&b.matches("\\d{4}-\\d{2}-\\d{2}")&&b.compareTo(a)<0)fail(errors,(path.isEmpty()?"":path+".")+pair[1],"must not be before "+pair[0]);
   }
   if(node.hasNonNull("normalLow")&&node.hasNonNull("normalHigh")&&node.get("normalLow").isNumber()&&node.get("normalHigh").isNumber()&&node.get("normalLow").decimalValue().compareTo(node.get("normalHigh").decimalValue())>0)fail(errors,path+".normalHigh","must not be below the lower reference limit");
   if(node.hasNonNull("dateOfBirth")){String date=node.get("dateOfBirth").asText();if(date.matches("\\d{4}-\\d{2}-\\d{2}")&&date.compareTo(LocalDate.now().toString())>0)fail(errors,path+".dateOfBirth","cannot be in the future");}
   if(node.path("ageValue").isNumber()){double divisor=switch(node.path("ageUnit").asText("Years")){case "Days"->365.25;case "Weeks"->52.18;case "Months"->12;case "Decades"->.1;default->1;};if(node.get("ageValue").asDouble()/divisor>150)fail(errors,path+".ageValue","must represent an age of 150 years or less");}
   return;
  }
  if(node.isArray()){if(node.size()>config.path("maxItems").asInt(1000))fail(errors,path,"too many entries");else for(int i=0;i<node.size();i++)walk(node.get(i),path+"["+i+"]",context,errors,depth+1);return;}
  String key=path.substring(path.lastIndexOf('.')+1);JsonNode rule=rule(context,path,key);
  if(rule.path("number").asBoolean()&&!node.isNumber()){fail(errors,path,"enter a numeric value");return;}
  if(node.isTextual()){
   String value=node.asText();if(Set.of("password","confirmPassword","newPassword").contains(key)){if(value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)fail(errors,path,"maximum 72 UTF-8 bytes");return;}
   int max=rule.path("max").asInt(config.path("defaultMax").asInt(10000));if(value.length()>max){fail(errors,path,"maximum "+max+" characters");return;}if(value.isEmpty())return;
   if(value.isBlank()){fail(errors,path,"enter a value or leave this optional field empty");return;}
   if(value.chars().anyMatch(c->c<32&&c!=9&&c!=10&&c!=13||c==127)){fail(errors,path,"contains unsupported control characters");return;}
   String v=value.trim();
   if(rule.path("date").asBoolean()){try{if(!v.matches("\\d{4}-\\d{2}-\\d{2}"))throw new IllegalArgumentException();LocalDate.parse(v);}catch(Exception e){fail(errors,path,"enter a valid calendar date (YYYY-MM-DD)");}}
   if(rule.path("instant").asBoolean()){try{java.time.Instant.parse(v);}catch(Exception e){fail(errors,path,"enter a valid date and time with timezone");}}
   if(rule.has("pattern")&&!Pattern.matches(rule.get("pattern").asText(),v))fail(errors,path,"use the expected format for this field");
   if(rule.path("email").asBoolean()&&!v.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))fail(errors,path,"enter a valid email address");
   if(rule.path("phone").asBoolean()){long digits=v.chars().filter(Character::isDigit).count();if(digits<7||digits>15)fail(errors,path,"enter a phone number containing 7–15 digits");}
   if(rule.has("enum")){boolean found=false;for(JsonNode choice:rule.get("enum"))if(choice.asText().equals(v))found=true;if(!found)fail(errors,path,"select a supported value");}
   if(context.equals("registration")){String choiceKey=path.startsWith("details.")?key:path;JsonNode choices=config.path("registrationChoices").path(choiceKey);if(choices.isArray()){boolean found=false;for(JsonNode c:choices)if(c.asText().equals(v))found=true;if(!found)fail(errors,path,"select an available registration option");}}
  }else if(node.isNumber()&&rule.path("number").asBoolean()){
   BigDecimal number=node.decimalValue();if(number.compareTo(rule.path("min").decimalValue())<0||number.compareTo(rule.path("max").decimalValue())>0)fail(errors,path,"must be between "+rule.path("min").asText()+" and "+rule.path("max").asText());
   if(number.stripTrailingZeros().scale()>rule.path("scale").asInt())fail(errors,path,"maximum "+rule.path("scale").asInt()+" decimal places");
  }else if(rule.path("number").asBoolean())fail(errors,path,"enter a numeric value");
 }
}
