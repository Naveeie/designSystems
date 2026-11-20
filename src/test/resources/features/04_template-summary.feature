Feature: Handle conversation events

  Background:
    Given Conversation id is "conv_id" ExternalConversation id is "ext_conv" Tenant id is "test-tenant-id" Environment is "dev" BusinessProcess id is "test-bp" and Experience id is "test-experience-id"

  Scenario: Handling conversation started event
    When Conversation Started
    Then the conversation started event should be present in cache
    Then the conversation started event should be stored in database

  Scenario: Entity Added Event
    Given Entity id is "113_uid" Entity instance id is "ins122" Entity Type is "Nlp" and value is "entity_name"
    When Entity is added
    Then validating entity added in summary doc from cache
    Then validating entity added in summary doc from database

  Scenario: Entity Updated Event
    Given Entity id is "113_uid" Entity instance id is "144ins" Entity Type is "Rule" and value is "entity_age"
    When Entity is updated
    Then validating entity updated in summary doc from cache
    Then validating entity updated in summary doc from database

  Scenario: Variable Added Event
    Given VariableInstance id is "instan12" Ai value is "ai_value" Name is "name_1" Value is "value_1" Variable Id is "variable1"
    When Variable is Added
    Then validating variable added in summary doc from cache
    Then validating variable added in summary doc from database

  Scenario: Variable Update Event
    Given VariableInstance id is "instan14" Ai value is "ai_value1" Name is "name_2" Value is "value_1" Variable Id is "variable1"
    When Variable is Updated
    Then validating variable updated in summary doc from cache
    Then validating variable updated in summary doc from database

  Scenario: Intent Added Event
    Given Text is "text_1" Intent InstanceId is "ins1111" Source is "ai" and IntentId is "1"
    When Intent is Added
    Then validating intent template present in summary doc
    Then validating entity associated with intent updated in template
    Then validating variable associated with intent updated in template

  Scenario: New Entity Updated in Template
    Given Entity id is "113_uid" Entity instance id is "244ins" Entity Type is "Rule" and value is "entity_number"
    When Entity is updated
    Then validating entity before deletion in template

  Scenario: New Variable Updated in Template
    Given VariableInstance id is "instan45" Ai value is "ai_value5" Name is "name_2" Value is "value_4" Variable Id is "variable1"
    When Variable is Updated
    Then validating variable added in summary doc from database

  Scenario: Intent Deleted Event
    Given Text is "text_1" Intent InstanceId is "ins1111" Source is "ai" and IntentId is "1"
    When Intent is Deleted
    Then the intent Deleted event should be processed successfully
