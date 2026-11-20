Feature:Delete scenarios of Header Template,Intent Template and Gen-Ai Configs

  Scenario: delete header template
    Given tenant id "test-tenant-id" and experience "test-experience-id" and business process "test-bp" and environment "dev"
    When deleting summary header template
    Then response is 200
    And header template is deleted
    And header template is deleted in cache

  Scenario: delete intent template
    Given tenantId "test-tenant-id" and experience "test-experience-id" and business process "test-bp" and environment "dev"
    When deleting intent template
    Then the response is 200
    And intent template is deleted
    And intent template is deleted in cache

  Scenario: delete config for tenant and experience
    Given tenant id "test-tenant-id" and experience "test-experience-id"
    When deleting gen-ai configuration
    Then response should be 200
    And gen-ai configuration should be deleted
    And gen-ai configuration should be deleted from cache
