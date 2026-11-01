package com.pharmasafe.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.common.filespecification.*;
import org.springframework.stereotype.Component;
import java.io.*;
import java.awt.Color;
import java.util.*;
import java.util.List;

/** No HTML, remote resources or user-supplied PDFs are executed while rendering. */
@Component
public class CasePdfRenderer {
 public byte[] render(JsonNode snapshot,byte[] originalJson) throws IOException {
  try(var writer=new Writer(snapshot)){return writer.render(originalJson);}
 }
 private static final class Writer implements AutoCloseable {
  final PDDocument doc=new PDDocument(); final JsonNode source; final PDFont font;
  final Map<Integer,String> glyphs=new HashMap<>(); final float left=42,width=511,bottom=55,line=12;
  PDPageContentStream stream; float y;String section;boolean alternate;
  Writer(JsonNode source)throws IOException {
   this.source=source;
   var resource=CasePdfRenderer.class.getResourceAsStream("/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf");
   if(resource==null)throw new IOException("Embedded report font is unavailable");
   try(resource){font=PDType0Font.load(doc,resource,true);}
  }
  byte[] render(byte[] json)throws IOException {
   var control=source.path("documentControl");
   doc.getDocumentInformation().setTitle("PharmaSafe Safety Suite - "+control.path("caseNumber").asText());
   doc.getDocumentInformation().setAuthor(control.path("generatedBy").asText());
   newPage();
   var fields=source.fields();while(fields.hasNext()){var field=fields.next();section(label(field.getKey()));walk(field.getValue(),"");}
   section("Reading this document");row("Source preservation","All stored fields in the selected document are listed, including empty values. Attachments are metadata only; original uploaded files are not embedded.");
   row("Character handling","Unsupported font characters appear as [U+XXXX]. The original UTF-8 snapshot is embedded as source-data.json; no source text is discarded.");
   row("Use","Internal case record. This PDF is not a regulatory submission, electronic signature, or evidence of regulatory validation.");
   stream.close();stream=null;
   int total=doc.getNumberOfPages();
   for(int i=0;i<total;i++)try(var footer=new PDPageContentStream(doc,doc.getPage(i),PDPageContentStream.AppendMode.APPEND,true,true)){
    footer.setStrokingColor(new Color(218,227,236));footer.moveTo(left,43);footer.lineTo(left+width,43);footer.stroke();
    text(footer,"CONFIDENTIAL - Internal case record",left,29,8,new Color(88,109,130));
    text(footer,"Page "+(i+1)+" of "+total,left+width-65,29,8,new Color(88,109,130));
   }
   PDComplexFileSpecification file=new PDComplexFileSpecification();file.setFile("source-data.json");file.setFileUnicode("source-data.json");file.setFileDescription("Original UTF-8 case document snapshot");
   PDEmbeddedFile embedded=new PDEmbeddedFile(doc,new ByteArrayInputStream(json));embedded.setSubtype("application/json");embedded.setSize(json.length);file.setEmbeddedFile(embedded);file.setEmbeddedFileUnicode(embedded);
   PDEmbeddedFilesNameTreeNode files=new PDEmbeddedFilesNameTreeNode();files.setNames(Map.of("source-data.json",file));PDDocumentNameDictionary names=new PDDocumentNameDictionary(doc.getDocumentCatalog());names.setEmbeddedFiles(files);doc.getDocumentCatalog().setNames(names);
   var out=new ByteArrayOutputStream();doc.save(out);return out.toByteArray();
  }
  void newPage()throws IOException {
   if(doc.getNumberOfPages()>=400)throw new IllegalArgumentException("This document exceeds 400 pages; narrow the selected document before exporting");
   if(stream!=null)stream.close();PDPage page=new PDPage(PDRectangle.A4);doc.addPage(page);stream=new PDPageContentStream(doc,page);
   stream.setNonStrokingColor(new Color(15,42,67));stream.addRect(0,754,595.28f,88);stream.fill();
   text(stream,"PharmaSafe Safety Suite",left,811,16,Color.WHITE);
   var control=source.path("documentControl");String kind=control.path("documentKind").asText().equals("MEDICAL_SUMMARY")?"MEDICAL SUMMARY":"COMPLETE CASE FORM";
   text(stream,kind+" | "+(control.path("finalDocument").asBoolean()?"FINAL RECORD":"WORKING COPY"),left,789,10,new Color(194,216,238));
   text(stream,safe(control.path("caseNumber").asText()),left,771,9,Color.WHITE);
   y=733;if(section!=null)sectionHeader(true);
  }
  void section(String name)throws IOException {section=name;alternate=false;if(y<bottom+90)newPage();else sectionHeader(false);}
  void sectionHeader(boolean continued)throws IOException {
   text(stream,section+(continued?" (continued)":""),left,y-14,11,new Color(15,42,67));y-=29;
   stream.setNonStrokingColor(new Color(226,234,243));stream.addRect(left,y-23,width,23);stream.fill();
   text(stream,"Field",left+9,y-15,9,new Color(33,59,84));text(stream,"Recorded value",left+169,y-15,9,new Color(33,59,84));y-=23;
  }
  void walk(JsonNode node,String prefix)throws IOException {
   if(node.isObject()){
    if(node.isEmpty())row(prefix.isEmpty()?"Record":prefix,"No fields recorded");
    var fields=node.fields();while(fields.hasNext()){var field=fields.next();walk(field.getValue(),prefix.isEmpty()?label(field.getKey()):prefix+" / "+label(field.getKey()));}
   }else if(node.isArray()){
    if(node.isEmpty())row(prefix.isEmpty()?"Records":prefix,"None recorded");
    for(int i=0;i<node.size();i++){if(node.get(i).isContainerNode()) {row((prefix.isEmpty()?"Record":prefix)+" "+(i+1),"Entry "+(i+1)+" of "+node.size());walk(node.get(i),prefix.isEmpty()?"":prefix);}else walk(node.get(i),prefix+" "+(i+1));}
   }else row(prefix,node.isNull()||node.asText().isEmpty()?"Not recorded":node.isBoolean()?(node.asBoolean()?"Yes":"No"):node.asText());
  }
  void row(String key,String value)throws IOException {
   var keys=wrap(safe(key),142,8.5f);var values=wrap(safe(value),333,9);int offset=0,total=Math.max(keys.size(),values.size());
   while(offset<total){
    if(y<bottom+line+16)newPage();int fit=Math.max(1,(int)((y-bottom-16)/line));int count=Math.min(fit,total-offset);float height=count*line+16;
    stream.setNonStrokingColor(alternate?new Color(247,249,252):Color.WHITE);stream.addRect(left,y-height,width,height);stream.fill();stream.setStrokingColor(new Color(220,229,239));stream.setLineWidth(.45f);stream.addRect(left,y-height,width,height);stream.moveTo(left+160,y);stream.lineTo(left+160,y-height);stream.stroke();
    for(int i=0;i<count;i++){int index=offset+i;float baseline=y-15-i*line;if(index<keys.size())text(stream,keys.get(index),left+9,baseline,8.5f,new Color(69,90,110));if(index<values.size())text(stream,values.get(index),left+169,baseline,9,new Color(25,45,65));}
    y-=height;offset+=count;if(offset<total)newPage();
   }alternate=!alternate;
  }
  List<String> wrap(String s,float max,float size)throws IOException {
   List<String> result=new ArrayList<>();
   for(String paragraph:s.split("\\n",-1)){
    if(paragraph.isEmpty()){result.add("");continue;}StringBuilder current=new StringBuilder();
    for(int offset=0;offset<paragraph.length();){int cp=paragraph.codePointAt(offset);String next=new String(Character.toChars(cp));offset+=Character.charCount(cp);
     if(current.length()>0&&font.getStringWidth(current+next)*size/1000>max){result.add(current.toString());current.setLength(0);}current.append(next);
    }if(!current.isEmpty())result.add(current.toString());
   }return result;
  }
  String safe(String value){StringBuilder out=new StringBuilder();value.replace("\r\n","\n").replace('\r','\n').codePoints().forEach(cp->{if(cp=='\n')out.append('\n');else if(cp=='\t')out.append("    ");else out.append(glyphs.computeIfAbsent(cp,c->{if(Character.isISOControl(c))return String.format("[U+%04X]",c);String t=new String(Character.toChars(c));try{font.encode(t);return t;}catch(Exception e){return String.format("[U+%04X]",c);}}));});return out.toString();}
  void text(PDPageContentStream target,String value,float x,float y,float size,Color color)throws IOException {target.beginText();target.setFont(font,size);target.setNonStrokingColor(color);target.newLineAtOffset(x,y);target.showText(value);target.endText();}
  String label(String s){return s.replaceAll("([a-z0-9])([A-Z])","$1 $2").replace('_',' ');}
  public void close()throws IOException {if(stream!=null)stream.close();doc.close();}
 }
}
