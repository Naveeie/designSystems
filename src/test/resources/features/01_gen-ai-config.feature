Feature: create and update gen-ai config
  integration test to create and update gen ai config

  Scenario: create config for tenant and experience
    Given tenant id "test-tenant-id" and experience "test-experience-id"
    When creating gen-ai configuration
    Then response should be 200
    And gen-ai configuration should be created
    And gen-ai configuration should be stored in cache

  Scenario: fetch gen-ai summary config
    Given tenant id "test-tenant-id" and experience "test-experience-id"
    When fetching gen-ai summary configuration with experience "test-experience-id"
    Then response should be 200
    And gen-ai configuration should contain tenant id "test-tenant-id" and environment "dev"

  Scenario: update config for tenant and experience
    Given tenant id "test-tenant-id" and experience "test-experience-id"
    When updating gen-ai configuration
    Then response should be 200
    And gen-ai configuration should be updated
    And gen-ai configuration should be updated in cache

  Scenario: validate entities under gen-ai summary
    Given entity IDs "entity1,entity2,entity3" and experience "test-exp-id"
    When validating entities under gen-ai summary
    Then response should be 200

  Scenario: fetch gen-ai Summary target lang
    When fetching gen-ai summary target language
    Then response should be 200

