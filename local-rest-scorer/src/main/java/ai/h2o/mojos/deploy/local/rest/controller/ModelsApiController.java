package ai.h2o.mojos.deploy.local.rest.controller;

import ai.h2o.mojos.deploy.common.rest.api.ModelApi;
import ai.h2o.mojos.deploy.common.rest.model.CapabilityType;
import ai.h2o.mojos.deploy.common.rest.model.ContributionRequest;
import ai.h2o.mojos.deploy.common.rest.model.ContributionResponse;
import ai.h2o.mojos.deploy.common.rest.model.Model;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
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
    this.supportedCapabilities = assembleSupportedCapabilities(
        scorer.getEnabledShapleyTypes(), scorer.isPredictionIntervalSupport()
    );
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
      scoreResponse.id(getScorerModelId());
      return ResponseEntity.ok(scoreResponse);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          String.format("Invalid scoring request due to: %s", e.getMessage()), e);
    } catch (Exception e) {
      throw new ResponseStatusException(
          ErrorUtil.translateErrorCode(e),
          String.format("Failed scoring request due to: %s", e.getMessage()), e);
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
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          String.format("Failed loading CSV file due to: %s", e.getMessage()), e);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          String.format("Invalid scoring request for CSV file due to: %s", e.getMessage()), e);
    } catch (Exception e) {
      throw new ResponseStatusException(
          ErrorUtil.translateErrorCode(e),
          String.format("Failed scoring CSV file due to: %s", e.getMessage()), e);
    }
  }

  @Override
  public ResponseEntity<ContributionResponse> getContribution(
          ContributionRequest request) {
    try {
      log.info("Got shapley contribution request");
      ContributionResponse contributionResponse
              = scorer.computeContribution(request);
      return ResponseEntity.ok(contributionResponse);
    } catch (UnsupportedOperationException e) {
      throw new ResponseStatusException(
          HttpStatus.NOT_IMPLEMENTED,
          String.format("Unsupported operation due to: %s", e.getMessage()), e);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          String.format("Invalid shapley contribution request due to: %s", e.getMessage()), e);
    } catch (Exception e) {
      throw new ResponseStatusException(
          ErrorUtil.translateErrorCode(e),
          String.format("Failed shapley contribution request due to: %s", e.getMessage()), e);
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
        result.addAll(Arrays.asList(
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
