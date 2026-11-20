package com.uniphore.uassist.summary.config;

import com.uniphore.uassist.summary.conf.MongoMapKeyDotReplacementConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class MongoMapKeyDotReplacementConfigurationTest {

  @Mock
  private MappingMongoConverter mongoConverter;

  @InjectMocks
  private MongoMapKeyDotReplacementConfiguration mongoMapKeyDotReplacementConfiguration;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  public void testSetMapKeyDotReplacement() {
    mongoMapKeyDotReplacementConfiguration.setMapKeyDotReplacement(mongoConverter);
    verify(mongoConverter, times(1)).setMapKeyDotReplacement("#");
  }
}
