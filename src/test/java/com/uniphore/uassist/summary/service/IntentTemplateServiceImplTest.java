package com.uniphore.uassist.summary.service;

import com.uniphore.platform.core.tenant.TenantContext;
import com.uniphore.uassist.summary.ApplicationTest;
import com.uniphore.uassist.summary.common.Constants;
import com.uniphore.uassist.summary.dao.IntentTemplateDao;
import com.uniphore.uassist.summary.dao.IntentTemplateRepo;
import com.uniphore.uassist.summary.dao.documents.IntentTemplateDoc;
import com.uniphore.uassist.summary.execption.CustomException;
import com.uniphore.uassist.summary.helper.DataHelper;
import com.uniphore.uassist.summary.model.IntentTemplate;
import com.uniphore.uassist.summary.model.MessageResponse;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;

import static com.uniphore.uassist.summary.utils.TestConstant.USERNAME;

@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Testcontainers
public class IntentTemplateServiceImplTest extends ApplicationTest {
  @Autowired
  private IntentTemplateService intentTemplateService;
  @MockBean
  private IntentTemplateDao intentTemplateDao;
  @MockBean
  private IntentTemplateRepo intentTemplateRepo;
  @Autowired
  private DataHelper datahelper;

  @Container
  public static MongoDBContainer container = new MongoDBContainer(DockerImageName.parse("mongo:5"));

  static {
    container.start();
  }

  @DynamicPropertySource
  static void mongoDbProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.mongodb.uri", container::getReplicaSetUrl);
  }

  @BeforeAll
  void setupData() {
    TenantContext.setTenantContext("3e37631c-78ac-4137-8415-72dcd90b19fd", USERNAME);
  }

  @Test
  @Order(1)
  void testUpsertIntentTemplateWithError() {
    try {
      Mockito.when(this.intentTemplateDao.upsertIntentTemplate(null))
          .thenThrow(new RuntimeException("Internal Server Error"));
      Assertions.assertThrows(CustomException.class, () -> {
        this.intentTemplateService.upsertIntentTemplate(null);
      });
    } catch (Exception e) {
      log.error("Exception in testUpsertIntentTemplateWithError", e);
      Assertions.fail("Exception in testUpsertIntentTemplateWithError");
    }
  }

  @Order(2)
  @Test
  void upsertIntentTemplateWithValidData() {
    try {
      IntentTemplate intentTemplateReq = this.datahelper.inputIntentTemplate();
      IntentTemplateDoc intentTemplateDoc = this.datahelper.inputIntentTemplateDoc();
      Mockito.when(intentTemplateDao.upsertIntentTemplate(intentTemplateReq))
          .thenReturn(intentTemplateDoc);
      MessageResponse response = intentTemplateService.upsertIntentTemplate(intentTemplateReq);
      Assertions.assertNotNull(response);
      Assertions.assertEquals(Constants.SUCCESS, response.getMessage());
      Assertions.assertEquals("fieldId", response.getId());
    } catch (Exception e) {
      log.error("Exception in upsertIntentTemplateWithValidData", e);
      Assertions.fail("Exception in upsertIntentTemplateWithValidData");
    }
  }

  @Order(3)
  @Test
  void upsertIntentTemplateWithNullResponse() {
    try {
      IntentTemplate intentTemplateReq = this.datahelper.inputIntentTemplate();
      Mockito.when(intentTemplateDao.upsertIntentTemplate(intentTemplateReq)).thenReturn(null);
      MessageResponse response = intentTemplateService.upsertIntentTemplate(intentTemplateReq);
      Assertions.assertNotNull(response);
      Assertions.assertEquals(Constants.FAILURE, response.getMessage());
      Assertions.assertEquals(Constants.EMPTY, response.getId());
    } catch (Exception e) {
      log.error("Exception in upsertIntentTemplateWithValidData", e);
      Assertions.fail("Exception in upsertIntentTemplateWithValidData");
    }
  }

  @Order(4)
  @Test
  void upsertIntentTemplateWithException() {
    IntentTemplate intentTemplateReq = this.datahelper.inputIntentTemplate();
    Mockito.when(intentTemplateDao.upsertIntentTemplate(intentTemplateReq))
        .thenThrow(new RuntimeException("Internal Server Error"));
    Assertions.assertThrows(CustomException.class, () -> {
      intentTemplateService.upsertIntentTemplate(intentTemplateReq);
    });
  }

  @Test
  @Order(5)
  void testGetIntentTemplate() {
    try {
      this.intentTemplateService.getIntentTemplate("intent1");
    } catch (Exception e) {
      log.error("Exception in testGetIntentTemplate", e);
      Assertions.fail("Exception in testGetIntentTemplate");
    }
  }

  @Test
  @Order(6)
  void testGetIntentTemplateWithError() {
    try {
      Mockito.when(this.intentTemplateService.getIntentTemplate(null))
          .thenThrow(new RuntimeException("Internal Server Error"));
      Assertions.assertThrows(CustomException.class, () -> {
        this.intentTemplateService.getIntentTemplate(null);
      });
    } catch (Exception e) {
      log.error("Exception in testGetIntentTemplateWithError", e);
      Assertions.fail("Exception in testGetIntentTemplateWithError");
    }
  }

  @Test
  @Order(7)
  void testGetIntentTemplatesList() {
    try {
      List<String> bp = new ArrayList();
      bp.add("qwerty");
      this.intentTemplateService.getIntentTemplates(bp);
    } catch (Exception e) {
      log.error("Exception in testGetIntentTemplatesList", e);
      Assertions.fail("Exception in testGetIntentTemplatesList");
    }
  }

  @Test
  @Order(8)
  void testGetIntentTemplatesWithError() {
    try {
      Mockito.when(this.intentTemplateDao.fetchIntentTemplates(null))
          .thenThrow(new RuntimeException("Internal Server Error"));
      Assertions.assertThrows(CustomException.class, () -> {
        this.intentTemplateService.getIntentTemplates(null);
      });
    } catch (Exception e) {
      log.error("Exception in testGetIntentTemplatesWithError", e);
      Assertions.fail("Exception in testGetIntentTemplatesWithError");
    }
  }

  @Test
  @Order(9)
  void testIsVariableUsedInSummary() {
    try {
      List<String> bp = new ArrayList<>();
      bp.add("qwerty");
      Mockito.when(this.intentTemplateDao.fetchTemplates()).thenReturn(bp);
      this.intentTemplateService.isVariableUsedInSummary("variable1");
    } catch (Exception e) {
      log.error("Exception in testIsVariableUsedInSummary", e);
      Assertions.fail("Exception in testIsVariableUsedInSummary");
    }
  }

  @Test
  @Order(10)
  void testIsVariableUsedInSummaryWithException() {
    try {
      Mockito.when(this.intentTemplateDao.fetchTemplates())
          .thenThrow(new RuntimeException("Internal Server Error"));
      Assertions.assertThrows(CustomException.class, () -> {
        this.intentTemplateService.isVariableUsedInSummary(null);
      });
    } catch (Exception e) {
      log.error("Exception in testIsVariableUsedInSummaryWithException", e);
      Assertions.fail("Exception in testIsVariableUsedInSummaryWithException");
    }
  }
  @Test
  @Order(11)
  void testGetIntentTemplatesByExpId_success() {
    String expId = "exp123";
    List<IntentTemplate> expected = new ArrayList<>();
    expected.add(new IntentTemplate());
    Mockito.when(intentTemplateDao.fetchIntentTemplatesByExpId(expId)).thenReturn(expected);

    List<IntentTemplate> result = intentTemplateService.getIntentTemplatesByExpId(expId);
    Assertions.assertNotNull(result);
    Assertions.assertEquals(1, result.size());
  }

  @Test
  @Order(12)
  void testGetIntentTemplatesByExpId_exception() {
    String expId = "exp123";
    Mockito.when(intentTemplateDao.fetchIntentTemplatesByExpId(expId))
        .thenThrow(new RuntimeException("DB error"));

    Assertions.assertThrows(CustomException.class, () -> {
      intentTemplateService.getIntentTemplatesByExpId(expId);
    });
  }

  @Test
  @Order(13)
  void testDeleteIntentTemplateByExpId_success() {
    String expId = "exp123";
    Assertions.assertDoesNotThrow(() -> {
      intentTemplateService.deleteIntentTemplateByExpId(expId);
    });
    Mockito.verify(intentTemplateDao, Mockito.times(1)).deleteIntentTemplatesByExpId(expId);
  }

  @Test
  @Order(14)
  void testDeleteIntentTemplateByExpId_exception() {
    String expId = "exp123";
    Mockito.doThrow(new RuntimeException("DB error"))
        .when(intentTemplateDao).deleteIntentTemplatesByExpId(expId);
    Assertions.assertThrows(CustomException.class, () -> {
      intentTemplateService.deleteIntentTemplateByExpId(expId);
    });
  }

  @AfterAll
  static void stopContainer() {
    container.stop();
  }

}
