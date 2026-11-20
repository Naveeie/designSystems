package com.uniphore.uassist.summary.dao;

import com.uniphore.platform.core.tenant.TenantConstant;
import com.uniphore.platform.core.tenant.TenantContext;
import com.uniphore.uassist.summary.dao.documents.AgentStepsDoc;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class AgentStepsDaoTest {

  @Mock
  private AgentStepsRepo agentStepsRepo;

  @InjectMocks
  private AgentStepsDao agentStepsDao;

  @Container
  public static MongoDBContainer container = new MongoDBContainer(DockerImageName.parse("mongo:5"));

  static {
    container.start();
  }

  @DynamicPropertySource
  static void mongoDbProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.mongodb.uri", container::getReplicaSetUrl);
  }

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    Map<String, String> tenantMap = new HashMap<>();
    tenantMap.put(TenantConstant.META_KEY_TENANT_ID, "3e37631c-78ac-4137-8415-72dcd90b19fd");
    tenantMap.put(TenantConstant.META_KEY_ENVIRONMENT, "dev");
    TenantContext.setMap(tenantMap);
  }

  @Test
  void testSaveAgentSteps() throws Exception {
    AgentStepsDoc doc =
        AgentStepsDoc.builder().conversationId("conv123").tenantId("tenant456").environment("prod")
            .build();
    agentStepsDao.saveAgentSteps(doc);
    verify(agentStepsRepo, times(1)).save(doc);
  }

  @Test
  void testGetAgentStepsByConvId() {
    String conversationId = "conv123";
    AgentStepsDoc doc = AgentStepsDoc.builder().conversationId(conversationId).tenantId("tenant456")
        .environment("prod").build();
    when(agentStepsRepo.findById(conversationId)).thenReturn(Optional.of(doc));
    Optional<AgentStepsDoc> result = agentStepsDao.getAgentStepsByConvId(conversationId);
    assertTrue(result.isPresent());
    assertEquals(conversationId, result.get().getConversationId());
    verify(agentStepsRepo, times(1)).findById(conversationId);
  }

  @AfterAll
  static void stopContainer() {
    container.stop();
  }

}
