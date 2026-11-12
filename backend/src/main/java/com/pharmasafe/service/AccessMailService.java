package com.pharmasafe.service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
@Service public class AccessMailService {
 private final ObjectProvider<JavaMailSender> sender;
 @Value("${app.access.public-url:}") private String publicUrl;
 @Value("${app.access.mail-from:}") private String from;
 public AccessMailService(ObjectProvider<JavaMailSender> sender){this.sender=sender;}
 public void sendReset(String address,String token){
  if(sender.getIfAvailable()==null||address==null||from.isBlank()||!publicUrl.startsWith("https://") && !publicUrl.startsWith("http://localhost:"))
   throw new IllegalStateException("Password reset delivery is not configured. Configure SMTP, the sender and the public frontend URL before approval.");
  var m=new SimpleMailMessage();m.setTo(address);m.setFrom(from);m.setSubject("PharmaSafe password reset");
  m.setText("Your administrator approved a password reset. Open this single-use link within 30 minutes: "+publicUrl.replaceAll("/+$", "")+"/#/reset-password?token="+token+"\nIf you did not request this, contact your administrator.");
  try{sender.getObject().send(m);}catch(Exception e){throw new IllegalStateException("Reset email could not be delivered. The request remains pending; check mail configuration and retry.");}
 }
}
