Feature: Create, fetch and update intent template
  integration test to create, fetch, update and  intent template

  Scenario: create intent template
    Given tenantId "test-tenant-id" and experience "test-experience-id" and business process "test-bp" and environment "dev"
    When creating intent template
    Then the response is 200
    And intent template should be created
    And intent template is set in cache

  Scenario: fetch intent template
    Given tenantId "test-tenant-id" and experience "test-experience-id" and business process "test-bp" and environment "dev"
    When getting intent template with intentId "1"
    Then the response is 200
    And response contains intent template "<p color='black'>customer name : <span entity-id='1' class='uniphore-entity-name'>customer name</span></p>" and business process "test-bp" and environment "dev" and tenant id "test-tenant-id"

  Scenario: update intent template
    Given tenantId "test-tenant-id" and experience "test-experience-id" and business process "test-bp" and environment "dev"
    When updating intent template
    Then the response is 200
    And intent template should be updated
    And intent template is updated in cache


