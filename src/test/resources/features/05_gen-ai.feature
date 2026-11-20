Feature: Handle gen-ai conversation events

  Background:
    Given For Gen-ai Conversation id is "conv_id" ExternalConversation id is "ext_conv" Tenant id is "test-tenant-id" Environment is "dev" BusinessProcess id is "bp_id" and Experience id is "test-experience-id"

  Scenario: Transcript Added Event
    Given Gen AI : Language is "en" ParticipantType is "participant_1" and Order is 23
    When Gen AI : Transcript is Added
    Then Gen AI : the transcript Added event should be processed successfully

  Scenario: Summary review After transcript added
    When Gen AI : summary-reviewed after transcript added
    Then Gen AI : Validating summary Gen AI after transcript added

  Scenario: Gen AI Conversation Status Update Event
    When Gen AI : Conversation Status
    Then Gen AI : the conversationUpdate should be processed successfully

  Scenario: Gen AI review After call end
    When Gen AI summary-reviewed
    Then Gen AI : Validating summary Gen AI
#
#
  Scenario: Gen AI Conversation Status Update Event Auto submit
    When Gen AI : summary-reviewed auto-submit
    Then Gen AI : Validating summary Gen AI auto-submit
