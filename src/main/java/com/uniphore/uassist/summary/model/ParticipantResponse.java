package com.uniphore.uassist.summary.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@lombok.Data
@lombok.AllArgsConstructor
@lombok.NoArgsConstructor
@JsonDeserialize(builder = ParticipantResponse.Builder.class)
@lombok.Builder(builderClassName = "Builder", toBuilder = true)
public class ParticipantResponse {
 private String participantId;
 private String type;
 private String ctiAgentId;
}
