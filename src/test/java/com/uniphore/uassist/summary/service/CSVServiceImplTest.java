package com.uniphore.uassist.summary.service;

import com.uniphore.platform.core.tenant.TenantConstant;
import com.uniphore.platform.core.tenant.TenantContext;
import com.uniphore.uassist.summary.ApplicationTest;
import com.uniphore.uassist.summary.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Testcontainers
public class CSVServiceImplTest {
  @InjectMocks
  private CSVService csvService;
  @Value("${summary.csv.upload-path}")
  private String storageLocation;

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
  @Order(1)
  void testDataToCSVFile() {
    try {
      String path = this.storageLocation + File.separator + Constants.SUMMARY_DETAILS_FOLDER;
      StringBuilder pathName = new StringBuilder(path);
      String fileName = ".test.csv";
      String[] data = {"word1,", "word2", "word3"};
      List<String[]> dataLines = new ArrayList<>();
      dataLines.add(data);
      this.csvService.dataToCSVFile(String.valueOf(pathName.append(fileName)), dataLines);
    } catch (final Exception e) {
      log.error("exception at testDataToCSVFile: {}", e);
      Assertions.fail("error: " + e.getMessage());
    }
  }

  @AfterAll
  static void stopContainer() {
    container.stop();
  }

}
