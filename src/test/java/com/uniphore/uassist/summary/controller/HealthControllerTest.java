package com.uniphore.uassist.summary.controller;

import com.uniphore.uassist.summary.ApplicationTest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class HealthControllerTest extends ApplicationTest {

  @Test
  @Order(1)
  void testStartupProbe() {
    try {
      final ResultActions action =
          this.mockMvc.perform(get("/health/startup").contentType(MediaType.APPLICATION_JSON))
              .andExpect(status().isOk());
      assertNotNull(action.andReturn().getResponse().getContentAsString());
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while starting up probe ", e);
    }
  }

  @Test
  @Order(2)
  void testLivenessProbe() {
    try {
      final ResultActions action =
          this.mockMvc.perform(get("/health/liveness").contentType(MediaType.APPLICATION_JSON))
              .andExpect(status().isOk());
      assertNotNull(action.andReturn().getResponse().getContentAsString());
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured in liveness probe ", e);
    }
  }

  @Test
  @Order(3)
  void testReadinessProbe() {
    try {
      final ResultActions action =
          this.mockMvc.perform(get("/health/readiness").contentType(MediaType.APPLICATION_JSON))
              .andExpect(status().isOk());
      assertNotNull(action.andReturn().getResponse().getContentAsString());
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured in readiness probe  ", e);
    }
  }
}
