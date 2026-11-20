package com.uniphore.uassist.summary.model;

import lombok.Getter;

@Getter
public enum AutoSubmitOption {
  MANUAL("manual"),
  AUTO_SUBMIT("autoSubmit"),
  START_OF_NEXT_CALL("startOfNextCall"),
  END_OF_CURRENT_CALL("endOfCurrentCall")
  ;

  private final String value;

  AutoSubmitOption(String value){
    this.value = value;
  }

}