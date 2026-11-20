package com.uniphore.uassist.summary.kafka;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import com.uniphore.uassist.summary.dao.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

import com.uniphore.platform.telemetry.context.ContextSetter;
import com.uniphore.uassist.summary.event.pojo.TenantOperation;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class TenantOperationEventConsumer {

  @Autowired
  private ExecutorService tenantOperationExecutorService;
  
  @Autowired
  private AgentEditsRepository aeRepo;
  
  @Autowired
  private DispositionRepo dRepo;
  
  @Autowired
  private GenAISummaryConfigRepo gascRepo;
  
  @Autowired
  private HeaderTemplateRepository htRepo;
  
  @Autowired
  private IntentTemplateRepo itRepo;
  
  @Autowired
  private SummaryRepository sRepo;
  
  @Autowired
  private SummaryUpdateLogDao sulRepo;

  @Autowired
  private AgentStepsRepo astRepo;

  @Bean
  @RegisterReflectionForBinding(classes = TenantOperation.class)
  public Consumer<TenantOperation> tenantOperation() {
    return tenantOp -> {
      if ("delete".equalsIgnoreCase(tenantOp.getOperation())) {
        log.info("Delete Tenant Data request received for tenant with tenantId {}",
            tenantOp.getTenantId());
        Callable<Boolean> deleteData = this.createDeleteTenantDataTask(tenantOp.getTenantId());
        tenantOperationExecutorService.submit(deleteData);
      } else {
        log.error("Invalid request received for tenant operation \"{}\" with tenantId {}",
            tenantOp.getOperation(), tenantOp.getTenantId());
      }
    };
  }

  public Callable<Boolean> createDeleteTenantDataTask(final String tenantId) {
    return () -> {
      ContextSetter.setContextWithSpan("uassist-summary", "delete-tenant", "", tenantId, "");
      aeRepo.deleteAgentEditsByTenantId(tenantId);
      dRepo.deleteDispositionByTenantId(tenantId);
      gascRepo.deleteGenAISummaryConfigByTenantId(tenantId);
      htRepo.deleteHeaderTemplateByTenantId(tenantId);
      itRepo.deleteIntentTemplateByTenantId(tenantId);
      sRepo.deleteSummaryByTenantId(tenantId);
      sulRepo.deleteSummaryEventLogByTenantId(tenantId);
      astRepo.deleteByTenantId(tenantId);
      log.info("Delete Tenant Data request finished for tenant with tenantId {}", tenantId);
      ContextSetter.clearContext();
      return true;
    };
  }
}
