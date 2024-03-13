package ai.h2o.mojos.deploy.gcp.vertex.ai.controller;

import ai.h2o.mojos.deploy.common.rest.model.Row;
import ai.h2o.mojos.deploy.common.rest.model.ScoreRequest;
import ai.h2o.mojos.deploy.common.rest.vertex.ai.api.ModelApi;
import ai.h2o.mojos.deploy.common.rest.vertex.ai.model.ScoreResponse;
import ai.h2o.mojos.deploy.common.transform.MojoScorer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class ModelsApiController implements ModelApi {
  private static final Logger log = LoggerFactory.getLogger(ModelsApiController.class);

  private final MojoScorer scorer;

  /**
   * Simple Api controller. Inherits from {@link ModelApi}, which controls global, expected request
   * mappings for the rest service.
   *
   * @param scorer {@link MojoScorer} initialized class containing loaded mojo, and mojo interaction
   *     methods
   */
  @Autowired
  public ModelsApiController(MojoScorer scorer) {
    this.scorer = scorer;
  }

  @Override
  public ResponseEntity<String> getModelId() {
    return ResponseEntity.ok(scorer.getModelId());
  }

  @Override
  public ResponseEntity<ScoreResponse> getScore(
      ai.h2o.mojos.deploy.common.rest.vertex.ai.model.ScoreRequest gcpRequest) {
    try {
      log.info("Got scoring request");
      // Convert GCP request to REST request
      ScoreRequest request = getRestScoreRequest(gcpRequest);
      ScoreResponse response = getGcpScoreResponse(scorer.score(request));
      // return ResponseEntity.ok(scorer.score(request));
      return ResponseEntity.ok().contentLength(
              new ObjectMapper().writeValueAsString(response).length()
      ).body(response);
    } catch (Exception e) {
      log.info("Failed scoring request: {}, due to: {}", gcpRequest, e.getMessage());
      log.debug(" - failure cause: ", e);
      return ResponseEntity.badRequest().build();
    }
  }

  /**
   * Converts GCP Vertex AI request to REST module request.
   *
   * @param gcpRequest {@link ai.h2o.mojos.deploy.common.rest.vertex.ai.model.ScoreRequest} GCP
   *     Vertex AI request to be converted
   */
  public static ScoreRequest getRestScoreRequest(
      ai.h2o.mojos.deploy.common.rest.vertex.ai.model.ScoreRequest gcpRequest) {
    ScoreRequest request = new ScoreRequest();

    if (gcpRequest.getParameters().getIncludeFieldsInOutput() != null) {
      request.setIncludeFieldsInOutput(gcpRequest.getParameters().getIncludeFieldsInOutput());
    }

    request.setNoFieldNamesInOutput(gcpRequest.getParameters().getNoFieldNamesInOutput());
    request.setIdField(gcpRequest.getParameters().getIdField());
    request.setFields(gcpRequest.getParameters().getFields());

    // Check if a pre-processing script was provided, if so, use it first
    Map<String, String> env = System.getenv();
    String preProccessingScript = env.getOrDefault("PREPROCESSING_SCRIPT_PATH", "");

    if (!preProccessingScript.isEmpty()) {
      // Write data to file to be injested by preprocessing script
      String fileName = UUID.randomUUID().toString() + ".json";
      ObjectMapper mapper = new ObjectMapper();
      try {
        mapper.writeValue(new File("/tmp/" + fileName), gcpRequest);
      } catch (IOException e) {
        log.error("Failed writing JSON file: {}", e.getMessage());
      }

      // Run preprocessing script on request data
      try (FileReader fileReader = new FileReader("/tmp/" + fileName);
          JsonReader reader = new JsonReader(fileReader)) {
        ProcessBuilder processBuilder;
        processBuilder = new ProcessBuilder("python", "/tmp/preprocessing_script.py", fileName);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        process.waitFor();

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        gcpRequest =
            gson.fromJson(
                reader, ai.h2o.mojos.deploy.common.rest.vertex.ai.model.ScoreRequest.class);
      } catch (JsonSyntaxException e) {
        log.error("Malformed JSON when reading from file: {}", e.getMessage());
      } catch (Exception e) {
        log.error("Unexpected error during data preprocessing step: {}", e.getMessage());
      } finally {
        try {
          Files.deleteIfExists(Paths.get("/tmp" + fileName));
        } catch (IOException e) {
          log.error("Failed deleting JSON file: {}", e.getMessage());
        }
      }
    }

    Row row;
    for (List<String> gcpRow : gcpRequest.getInstances()) {
      row = new Row();
      for (int i = 0; i < gcpRow.size(); i++) {
        row.add(gcpRow.get(i));
      }

      request.addRowsItem(row);
    }

    return request;
  }

  /**
   * Converts REST module response to GCP Vertex AI response.
   *
   * @param restResponse {@link ai.h2o.mojos.deploy.common.rest.model.ScoreResponse} REST module
   *     response to convert
   */
  public static ScoreResponse getGcpScoreResponse(
      ai.h2o.mojos.deploy.common.rest.model.ScoreResponse restResponse) {
    ScoreResponse response = new ScoreResponse();

    response.setId(restResponse.getId());
    response.setFields(restResponse.getFields());

    ai.h2o.mojos.deploy.common.rest.vertex.ai.model.Row row;
    for (List<String> restRow : restResponse.getScore()) {
      row = new ai.h2o.mojos.deploy.common.rest.vertex.ai.model.Row();
      for (int i = 0; i < restRow.size(); i++) {
        row.add(restRow.get(i));
      }

      response.addPredictionsItem(row);
    }

    return response;
  }
}
