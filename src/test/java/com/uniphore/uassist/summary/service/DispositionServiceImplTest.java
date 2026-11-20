package com.uniphore.uassist.summary.service;

import com.uniphore.uassist.summary.ApplicationTest;
import com.uniphore.uassist.summary.dao.DispositionDao;
import com.uniphore.uassist.summary.execption.CustomException;
import com.uniphore.uassist.summary.helper.DataHelper;
import com.uniphore.uassist.summary.model.Disposition;
import com.uniphore.uassist.summary.model.DispositionLevels;
import com.uniphore.uassist.summary.model.MessageResponse;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Testcontainers
public class DispositionServiceImplTest extends ApplicationTest {

  private static final String DISPOSITION_REQUEST_JSON = "disposition-request.json";
  private Disposition disposition;
  @Autowired
  private DispositionService dispositionService;
  @MockBean
  private DispositionDao dispositionDao;
  @Autowired
  private DataHelper dataHelper;

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
    try {
      disposition = this.dataHelper.inputDispositionResponse(DISPOSITION_REQUEST_JSON);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Test
  @Order(1)
  void testUpsertDisposition() {
    try {
      MessageResponse messageResponse = this.dispositionService.upsertDisposition(disposition);
      assertNotNull(messageResponse, "messageResponse is not null");
    } catch (final Exception e) {
      log.error("exception at testUpsertDisposition: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(2)
  void testUpsertDispositionWithError() {
    Mockito.when(this.dispositionService.upsertDisposition(null))
        .thenThrow(new CustomException("Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR));
    Assertions.assertThrows(NullPointerException.class, () -> {
      this.dispositionService.upsertDisposition(null);
    });
  }

  @Test
  @Order(3)
  void testGetDisposition() {
    try {
      disposition = this.dispositionService.getDisposition("bp1");
      assertNotNull(disposition, "disposition is not null");
    } catch (final Exception e) {
      log.error("exception at testGetDisposition: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(4)
  void testGetDispositionWithError() {
    try {
      Assertions.assertThrows(CustomException.class, () -> {
        this.dispositionService.getDisposition(null);
      });
    } catch (Exception e) {
      log.error("exception at testGetDispositionWithError: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(5)
  void testGetDispositionLevels() {
    try {
      List<String> bps = new ArrayList<>();
      bps.add("bp1");
      List<DispositionLevels> dispositionList = this.dispositionService.getDispositionLevels(bps);
      assertNotNull(dispositionList, "dispositionList is not null");
    } catch (final Exception e) {
      log.error("exception at testGetDispositionLevels: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @Test
  @Order(6)
  void testGetDispositionLevelsWithError() {
    try {
      Mockito.when(this.dispositionService.getDispositionLevels(null)).thenThrow(
          new CustomException("Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR));
      Assertions.assertThrows(CustomException.class, () -> {
        this.dispositionService.getDispositionLevels(null);
      });
    } catch (final Exception e) {
      log.error("exception at testGetDispositionLevels: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @AfterAll
  static void stopContainer() {
    container.stop();
  }

}
