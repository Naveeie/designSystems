package com.uniphore.uassist.summary.service;

import com.uniphore.uassist.summary.ApplicationTest;
import com.uniphore.uassist.summary.cache.CacheRefreshHelper;
import com.uniphore.uassist.summary.dao.HeaderTemplateDao;
import com.uniphore.uassist.summary.execption.CustomException;
import com.uniphore.uassist.summary.utils.TestConstant;
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

@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Testcontainers
public class HeaderTemplateServiceImplTest extends ApplicationTest {

  @Autowired
  private HeaderTemplateService headerTemplateService;
  @MockBean
  private HeaderTemplateDao headerTemplateDao;

  @MockBean
  private CacheRefreshHelper cacheRefreshHelper;

  @Container
  public static MongoDBContainer container = new MongoDBContainer(DockerImageName.parse("mongo:5"));

  static {
    container.start();
  }

  @DynamicPropertySource
  static void mongoDbProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.mongodb.uri", container::getReplicaSetUrl);
  }

  @Test
  @Order(1)
  void testUpsertHeaderTemplateWithError() {
    try {
      Assertions.assertThrows(CustomException.class, () -> {
        this.headerTemplateService.upsertHeaderTemplate(null);
      });
    } catch (final Exception e) {
      log.error("exception at testUpsertHeaderTemplateWithError: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(2)
  void testFetchHeaderTemplate() {
    try {
      this.headerTemplateService.fetchHeaderTemplate(TestConstant.BUSINESS_PROCESS);
    } catch (final Exception e) {
      log.error("exception at testFetchHeaderTemplate: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(3)
  void testFetchHeaderTemplateWithError() {
    try {
      Mockito.when(this.headerTemplateDao.fetchHeaderTemplate(null))
          .thenThrow(new RuntimeException("Internal Server Error"));
      Assertions.assertThrows(CustomException.class, () -> {
        this.headerTemplateService.fetchHeaderTemplate(null);
      });
    } catch (final Exception e) {
      log.error("exception at testFetchHeaderTemplateWithError: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(4)
  void testFetchHeaderTemplatesList() {
    try {
      List<String> bp = new ArrayList<>();
      bp.add("qwerty");
      this.headerTemplateService.fetchHeaderTemplates(bp);
    } catch (final Exception e) {
      log.error("exception at testFetchHeaderTemplatesList: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(5)
  void testFetchHeaderTemplatesWithError() {
    try {
      Mockito.when(this.headerTemplateDao.fetchHeaderTemplates(null))
          .thenThrow(new RuntimeException("Internal Server Error"));
      Assertions.assertThrows(CustomException.class, () -> {
        this.headerTemplateService.fetchHeaderTemplates(null);
      });
    } catch (final Exception e) {
      log.error("exception at testFetchHeaderTemplatesWithError: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @AfterAll
  static void stopContainer() {
    container.stop();
  }

}
