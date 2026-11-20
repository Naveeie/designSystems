Feature: Create, fetch and update header template
  integration test to create, fetch, update and delete header template

  Scenario: create header template
    Given tenant id "test-tenant-id" and experience "test-experience-id" and business process "test-bp" and environment "dev"
    When creating summary header template
    Then response is 200
    And header template should be created
    And header template is set in cache

  Scenario: fetch header template
    Given tenant id "test-tenant-id" and experience "test-experience-id"
    When getting summary header template with business process "test-bp"
    Then response is 200
    And response contains header template "<p color='black'>customer name : <span entity-id='1' class='uniphore-entity-name'>customer name</span></p>" and business process "test-bp" and environment "dev" and tenant id "test-tenant-id"

  Scenario: update header template
    Given tenant id "test-tenant-id" and experience "test-experience-id" and business process "test-bp" and environment "dev"
    When updating summary header template
    Then response is 200
    And header template should be updated
    And header template is updated in cache

