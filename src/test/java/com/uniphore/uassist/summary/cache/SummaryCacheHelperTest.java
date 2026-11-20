package com.uniphore.uassist.summary.cache;

import com.uniphore.platform.cache.CacheOperations;
import com.uniphore.platform.core.tenant.TenantConstant;
import com.uniphore.platform.core.tenant.TenantContext;
import com.uniphore.ri.auth.httpserver.TenantCommonFilter;
import com.uniphore.uassist.summary.ApplicationTest;
import com.uniphore.uassist.summary.dao.documents.SummaryDoc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.when;

public class SummaryCacheHelperTest extends ApplicationTest {

  @Autowired
  private SummaryCacheHelper summaryCacheHelper;

  @MockBean
  private CacheOperations<SummaryDoc> summaryCache;

  @BeforeEach
  public void init() {
    MockitoAnnotations.openMocks(this);
    Map<String, String> tenantMap = new HashMap<>();
    tenantMap.put(TenantConstant.META_KEY_TENANT_ID, "3e37631c-78ac-4137-8415-72dcd90b19fd");
    tenantMap.put(TenantConstant.META_KEY_ENVIRONMENT, "dev");
    TenantContext.setMap(tenantMap);
  }

  @Test
  void TestRemove() {
   when(this.summaryCache.delete(Mockito.anyString(),Mockito.anyString())).thenReturn(1L);
    summaryCacheHelper.remove("mock-conversation-id");
  }

  @Test
  void TestRemoveGenAIRequested() {
    when(this.summaryCache.delete(Mockito.anyString(),Mockito.anyString())).thenReturn(1L);
    summaryCacheHelper.removeGenAIRequested("mock-conversation-id");
  }


}
