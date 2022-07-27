package ai.h2o.mojos.deploy.local.rest.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import ai.h2o.mojos.deploy.common.rest.v1exp.model.ScoreMediaRequest;
import ai.h2o.mojos.deploy.common.rest.v1exp.model.ScoreResponse;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class ModelsApiControllerV1ExpTest {

  @Test
  void verifyScoreMedia_ReturnsUnimplemented() {
    // Given
    ScoreMediaRequest request = mock(ScoreMediaRequest.class);
    List<Resource> files = new ArrayList<>();
    ModelsApiControllerV1Exp controller = new ModelsApiControllerV1Exp();

    // When
    ResponseEntity<ScoreResponse> response =
        controller.getScoreMedia(request, files);

    // Then
    assertEquals(response.getStatusCode(), HttpStatus.NOT_IMPLEMENTED);
  }
}
