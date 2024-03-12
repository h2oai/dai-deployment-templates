package ai.h2o.mojos.deploy.local.rest.controller;

import ai.h2o.mojos.deploy.common.rest.api.ModelApi;
import ai.h2o.mojos.deploy.common.rest.model.CapabilityType;
import ai.h2o.mojos.deploy.common.rest.model.ContributionRequest;
import ai.h2o.mojos.deploy.common.rest.model.ContributionResponse;
import ai.h2o.mojos.deploy.common.rest.model.Model;
import ai.h2o.mojos.deploy.common.rest.model.ScoreMediaRequest;
import ai.h2o.mojos.deploy.common.rest.model.ScoreRequest;
import ai.h2o.mojos.deploy.common.rest.model.ScoreResponse;
import ai.h2o.mojos.deploy.common.transform.MojoScorer;
import ai.h2o.mojos.deploy.common.transform.SampleRequestBuilder;
import ai.h2o.mojos.deploy.common.transform.ShapleyLoadOption;
import ai.h2o.mojos.deploy.local.rest.error.ErrorUtil;
import com.google.common.base.Strings;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class ModelsApiController implements ModelApi {

  private static final String MODEL_ID = "MODEL_ID";
  private static final Logger log = LoggerFactory.getLogger(ModelsApiController.class);

  private final MojoScorer scorer;
  private final SampleRequestBuilder sampleRequestBuilder;

  private final List<CapabilityType> supportedCapabilities;

  /**
   * Simple Api controller. Inherits from {@link ModelApi}, which controls global, expected request
   * mappings for the rest service.
   *
   * @param scorer {@link MojoScorer} initialized class containing loaded mojo, and mojo interaction
   *     methods
   * @param sampleRequestBuilder {@link SampleRequestBuilder} initialized class, for generating
   *     sample request.
   */
  @Autowired
  public ModelsApiController(MojoScorer scorer, SampleRequestBuilder sampleRequestBuilder) {
    this.scorer = scorer;
    this.sampleRequestBuilder = sampleRequestBuilder;
    this.supportedCapabilities =
        assembleSupportedCapabilities(
            scorer.getEnabledShapleyTypes(), scorer.isPredictionIntervalSupport());
  }

  @Override
  public ResponseEntity<ScoreResponse> getMediaScore(
      ScoreMediaRequest request, List<MultipartFile> files) {
    log.info("Got score media request");
    throw new ResponseStatusException(
        HttpStatus.NOT_IMPLEMENTED, "score media files is not implemented");
  }

  @Override
  public ResponseEntity<Model> getModelInfo() {
    return ResponseEntity.ok(scorer.getModelInfo().id(getScorerModelId()));
  }

  @Override
  public ResponseEntity<String> getModelId() {
    return ResponseEntity.ok(getScorerModelId());
  }

  @Override
  public ResponseEntity<List<CapabilityType>> getCapabilities() {
    return ResponseEntity.ok(supportedCapabilities);
  }

  @Override
  public ResponseEntity<ScoreResponse> getScore(ScoreRequest request) {
    try {
      log.info("Got scoring request");
      ScoreResponse scoreResponse = scorer.score(request);
      log.info("finish scoring request");
      scoreResponse.id(getScorerModelId());
      return ResponseEntity.ok(scoreResponse);
    } catch (IllegalArgumentException e) {
      log.error("Invalid scoring request due to: {}", e.getMessage());
      log.debug(" - request content: ", request);
      log.debug(" - failure cause: ", e);
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          String.format("Invalid scoring request due to: %s", e.getMessage()),
          e);
    } catch (Exception e) {
      log.error("Failed scoring request due to: {}", e.getMessage());
      log.debug(" - request content: ", request);
      log.debug(" - failure cause: ", e);
      throw new ResponseStatusException(
          ErrorUtil.translateErrorCode(e),
          String.format("Failed scoring request due to: %s", e.getMessage()),
          e);
    }
  }

  @Override
  public ResponseEntity<ScoreResponse> getScoreByFile(String file) {
    if (Strings.isNullOrEmpty(file)) {
      log.info("Request is missing a valid CSV file path");
      return ResponseEntity.badRequest().build();
    }
    try {
      log.info("Got scoring request for CSV");
      return ResponseEntity.ok(scorer.scoreCsv(file));
    } catch (IOException e) {
      log.error("Failed loading CSV file {} due to: {}", file, e.getMessage());
      log.debug(" - failure cause: ", e);
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          String.format("Failed loading CSV file due to: %s", e.getMessage()),
          e);
    } catch (IllegalArgumentException e) {
      log.error("Invalid scoring request for CSV file {} request due to: {}", file, e.getMessage());
      log.debug(" - failure cause: ", e);
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          String.format("Invalid scoring request for CSV file due to: %s", e.getMessage()),
          e);
    } catch (Exception e) {
      log.error("Failed scoring CSV file {} due to: {}", file, e.getMessage());
      log.debug(" - failure cause: ", e);
      throw new ResponseStatusException(
          ErrorUtil.translateErrorCode(e),
          String.format("Failed scoring CSV file due to: %s", e.getMessage()),
          e);
    }
  }

  @Override
  public ResponseEntity<ContributionResponse> getContribution(ContributionRequest request) {
    try {
      log.info("Got shapley contribution request");
      ContributionResponse contributionResponse = scorer.computeContribution(request);
      return ResponseEntity.ok(contributionResponse);
    } catch (UnsupportedOperationException e) {
      log.error("Unsupported operation due to: {}", e.getMessage());
      throw new ResponseStatusException(
          HttpStatus.NOT_IMPLEMENTED,
          String.format("Unsupported operation due to: %s", e.getMessage()),
          e);
    } catch (IllegalArgumentException e) {
      log.error("Invalid shapley contribution request due to: {}", e.getMessage());
      log.debug(" - failure cause: ", e);
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          String.format("Invalid shapley contribution request due to: %s", e.getMessage()),
          e);
    } catch (Exception e) {
      log.error("Failed shapley contribution request due to: {}", e.getMessage());
      log.debug(" - failure cause: ", e);
      throw new ResponseStatusException(
          ErrorUtil.translateErrorCode(e),
          String.format("Failed shapley contribution request due to: %s", e.getMessage()),
          e);
    }
  }

  @Override
  public ResponseEntity<ScoreRequest> getSampleRequest() {
    return ResponseEntity.ok(sampleRequestBuilder.build(scorer.getPipeline().getInputMeta()));
  }

  private String getScorerModelId() {
    try {
      String res = System.getenv(MODEL_ID);
      return res;
    } catch (Exception ignored) {
      return scorer.getModelId();
    }
  }

  private static List<CapabilityType> assembleSupportedCapabilities(
      ShapleyLoadOption enabledShapleyTypes, boolean supportPredictionInterval) {
    List<CapabilityType> result = new ArrayList<>();
    if (supportPredictionInterval) {
      result.add(CapabilityType.SCORE_PREDICTION_INTERVAL);
    }
    switch (enabledShapleyTypes) {
      case ALL:
        result.addAll(
            Arrays.asList(
                CapabilityType.SCORE,
                CapabilityType.CONTRIBUTION_ORIGINAL,
                CapabilityType.CONTRIBUTION_TRANSFORMED));
        break;
      case ORIGINAL:
        result.addAll(Arrays.asList(CapabilityType.SCORE, CapabilityType.CONTRIBUTION_ORIGINAL));
        break;
      case TRANSFORMED:
        result.addAll(Arrays.asList(CapabilityType.SCORE, CapabilityType.CONTRIBUTION_TRANSFORMED));
        break;
      case NONE:
      default:
        result.add(CapabilityType.SCORE);
    }
    return result;
  }
}
