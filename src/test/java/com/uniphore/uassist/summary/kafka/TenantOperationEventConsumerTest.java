package com.uniphore.uassist.summary.kafka;

import com.uniphore.uassist.summary.dao.*;
import com.uniphore.uassist.summary.event.pojo.TenantOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
class TenantOperationEventConsumerTest {

  @InjectMocks
  private TenantOperationEventConsumer consumer;

  @Mock private ExecutorService tenantOperationExecutorService;
  @Mock private AgentEditsRepository aeRepo;
  @Mock private DispositionRepo dRepo;
  @Mock private GenAISummaryConfigRepo gascRepo;
  @Mock private HeaderTemplateRepository htRepo;
  @Mock private IntentTemplateRepo itRepo;
  @Mock private SummaryRepository sRepo;
  @Mock private SummaryUpdateLogDao sulRepo;
  @Mock private AgentStepsRepo astRepo;

  @BeforeEach
  void setup() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void testTenantOperationDelete_shouldSubmitTask() {
    TenantOperation op = new TenantOperation();
    op.setTenantId("tenant123");
    op.setOperation("delete");

    consumer.tenantOperation().accept(op);

    verify(tenantOperationExecutorService, times(1)).submit(any(Callable.class));
  }

  @Test
  void testTenantOperationInvalid_shouldNotSubmit() {
    TenantOperation op = new TenantOperation();
    op.setTenantId("tenant123");
    op.setOperation("update");

    consumer.tenantOperation().accept(op);

    verifyNoInteractions(tenantOperationExecutorService);
  }

  @Test
  void testCreateDeleteTenantDataTask_shouldCallAllRepositories() throws Exception {
    String tenantId = "tenant123";
    Callable<Boolean> task = consumer.createDeleteTenantDataTask(tenantId);

    Boolean result = task.call();

    verify(aeRepo).deleteAgentEditsByTenantId(tenantId);
    verify(dRepo).deleteDispositionByTenantId(tenantId);
    verify(gascRepo).deleteGenAISummaryConfigByTenantId(tenantId);
    verify(htRepo).deleteHeaderTemplateByTenantId(tenantId);
    verify(itRepo).deleteIntentTemplateByTenantId(tenantId);
    verify(sRepo).deleteSummaryByTenantId(tenantId);
    verify(sulRepo).deleteSummaryEventLogByTenantId(tenantId);
    verify(astRepo).deleteByTenantId(tenantId);
    assert result;
  }
}
