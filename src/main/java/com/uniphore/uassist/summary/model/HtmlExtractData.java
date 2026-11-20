package com.uniphore.uassist.summary.model;

@lombok.Data
public class HtmlExtractData {
  private String id;
  private String name;
  private String type;
  private String value;
  public HtmlExtractData(String id,String name,String type) {
    this.id=id;
    this.name=name;
    this.type=type;
  }
}
