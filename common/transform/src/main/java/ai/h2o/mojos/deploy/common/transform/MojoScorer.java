package ai.h2o.mojos.deploy.common.transform;

import ai.h2o.mojos.deploy.common.rest.model.ContributionRequest;
import ai.h2o.mojos.deploy.common.rest.model.ContributionResponse;
import ai.h2o.mojos.deploy.common.rest.model.Model;
import ai.h2o.mojos.deploy.common.rest.model.ScoreRequest;
import ai.h2o.mojos.deploy.common.rest.model.ScoreResponse;
import ai.h2o.mojos.deploy.common.rest.model.ScoringType;
import ai.h2o.mojos.deploy.common.rest.model.ShapleyType;
import ai.h2o.mojos.runtime.MojoPipeline;
import ai.h2o.mojos.runtime.api.MojoPipelineService;
import ai.h2o.mojos.runtime.frame.MojoFrame;
import ai.h2o.mojos.runtime.frame.MojoFrameMeta;
import ai.h2o.mojos.runtime.lic.LicenseException;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * H2O DAI mojo scorer.
 *
 * <p>The scorer code is shared for all mojo deployments and is only parameterized by the
 * {@code mojo.path} property to define the mojo to use.
 * {@code shapley.enable} property to enable shapley contribution.
 */
public class MojoScorer {
  private static final String ENABLE_SHAPLEY_CONTRIBUTION_MESSAGE
          = "shapley.types.enabled property has to be set to one of [TRANSFORMED, ORIGINAL, ALL]"
          + " or shapley.enable property has to be set to true in the runtime configuration "
          + "to obtain Shapley contribution";

  private static final Logger log = LoggerFactory.getLogger(MojoScorer.class);

  private static final String MOJO_PIPELINE_PATH_PROPERTY = "mojo.path";
  private static final String MOJO_PIPELINE_PATH = System.getProperty(MOJO_PIPELINE_PATH_PROPERTY);
  private static final MojoPipeline pipeline = loadMojoPipelineFromFile();

  private final ShapleyLoadOption enabledShapleyTypes;
  private final boolean shapleyEnabled;
  private static MojoPipeline pipelineTransformedShapley;
  private static MojoPipeline pipelineOriginalShapley;

  private final ScoreRequestToMojoFrameConverter scoreRequestConverter;
  private final MojoFrameToScoreResponseConverter scoreResponseConverter;
  private final MojoFrameToContributionResponseConverter contributionResponseConverter;
  private final ContributionRequestToMojoFrameConverter contributionRequestConverter;
  private final MojoPipelineToModelInfoConverter modelInfoConverter;
  private final CsvToMojoFrameConverter csvConverter;

  /**
   * MojoScorer class initializer, requires below parameters.
   *
   * @param scoreRequestConverter {@link ScoreRequestToMojoFrameConverter}
   * @param scoreResponseConverter {@link MojoFrameToScoreResponseConverter}
   * @param modelInfoConverter {@link MojoPipelineToModelInfoConverter}
   * @param csvConverter {@link CsvToMojoFrameConverter}
   */
  public MojoScorer(
      ScoreRequestToMojoFrameConverter scoreRequestConverter,
      MojoFrameToScoreResponseConverter scoreResponseConverter,
      ContributionRequestToMojoFrameConverter contributionRequestConverter,
      MojoFrameToContributionResponseConverter contributionResponseConverter,
      MojoPipelineToModelInfoConverter modelInfoConverter,
      CsvToMojoFrameConverter csvConverter) {
    this.scoreRequestConverter = scoreRequestConverter;
    this.scoreResponseConverter = scoreResponseConverter;
    this.contributionRequestConverter = contributionRequestConverter;
    this.contributionResponseConverter = contributionResponseConverter;
    this.modelInfoConverter = modelInfoConverter;
    this.csvConverter = csvConverter;

    this.enabledShapleyTypes = ShapleyLoadOption.fromEnvironment();
    this.shapleyEnabled = ShapleyLoadOption.isEnabled(enabledShapleyTypes);

    loadMojoPipelinesForShapley();
  }

  /**
   * Method to score an incoming request of type {@link ScoreRequest}.
   *
   * @param request {@link ScoreRequest}
   * @return response {@link ScoreResponse}
   */
  public ScoreResponse score(ScoreRequest request) {
    MojoFrame requestFrame = scoreRequestConverter
            .apply(request, pipeline.getInputFrameBuilder());
    MojoFrame responseFrame = doScore(requestFrame);
    ScoreResponse response = scoreResponseConverter.apply(responseFrame, request);
    response.id(pipeline.getUuid());

    ShapleyType requestShapleyType = request.getRequestShapleyValueType();
    if (requestShapleyType == null
            || requestShapleyType == ShapleyType.NONE) {
      return response;
    }

    if (!shapleyEnabled) {
      throw new IllegalArgumentException(ENABLE_SHAPLEY_CONTRIBUTION_MESSAGE);
    }

    if (!ShapleyLoadOption.requestedTypeEnabled(
        enabledShapleyTypes, requestShapleyType.toString())) {
      throw new IllegalArgumentException(
          String.format(
              "Requested Shapley type %s not enabled for this scorer. Expected: %s",
              requestShapleyType,
              enabledShapleyTypes));
    }

    try {
      switch (requestShapleyType) {
        case TRANSFORMED:
          response.setFeatureShapleyContributions(transformedFeatureContribution(request));
          break;
        case ORIGINAL:
          response.setFeatureShapleyContributions(originalFeatureContribution(request));
          break;
        default:
          log.info("Only ORIGINAL or TRANSFORMED are accepted enums values of Shapley values");
          break;
      }
    } catch (Exception e) {
      log.info("Failed shapley contribution due to: {}", e.getMessage());
      log.debug(" - failure cause: ", e);
      return response;
    }
    return response;
  }

  private ContributionResponse originalFeatureContribution(ScoreRequest request) {
    MojoFrame requestFrame = scoreRequestConverter
            .apply(request, pipelineOriginalShapley.getInputFrameBuilder());
    return contribution(doShapleyContrib(requestFrame, true));
  }

  private ContributionResponse transformedFeatureContribution(ScoreRequest request) {
    MojoFrame requestFrame = scoreRequestConverter
            .apply(request, pipelineTransformedShapley.getInputFrameBuilder());
    return contribution(doShapleyContrib(requestFrame, false));
  }

  /**
   * Method to get shapley values for an incoming request of type {@link ContributionRequest}.
   *
   * @param request {@link ContributionRequest}
   * @return response {@link ContributionResponse}
   */
  public ContributionResponse computeContribution(ContributionRequest request) {

    if (!shapleyEnabled) {
      throw new IllegalArgumentException(ENABLE_SHAPLEY_CONTRIBUTION_MESSAGE);
    }

    ShapleyType requestedShapleyType = request.getRequestShapleyValueType();
    MojoFrame requestFrame;

    switch (requestedShapleyType) {
      case TRANSFORMED:
        requestFrame = contributionRequestConverter
                .apply(request, pipelineTransformedShapley.getInputFrameBuilder());
        return contribution(doShapleyContrib(requestFrame, false));
      case ORIGINAL:
        requestFrame = contributionRequestConverter
                .apply(request, pipelineOriginalShapley.getInputFrameBuilder());
        return contribution(doShapleyContrib(requestFrame, true));
      default:
        throw new IllegalArgumentException(
                "Only ORIGINAL or TRANSFORMED are accepted enums values of Shapley values");
    }
  }

  private ContributionResponse contribution(MojoFrame contributionFrame) {

    MojoFrameMeta outputMeta = pipeline.getOutputMeta();
    ScoringType scoringType = scoringType(outputMeta.getColumns().size());
    List<String> outputGroupNames = getOutputGroups(outputMeta);

    if (ScoringType.CLASSIFICATION.equals(scoringType)) {
      return contributionResponseConverter
              .contributionResponseWithOutputGroup(contributionFrame, outputGroupNames);
    } else {
      return contributionResponseConverter
              .contributionResponseWithNoOutputGroup(contributionFrame);
    }
  }

  private List<String> getOutputGroups(MojoFrameMeta outputMeta) {
    int numberOutputColumns = outputMeta.getColumns().size();
    List<String> outputClass =  new ArrayList<>();
    for (int i = 0; i < numberOutputColumns; i++) {
      String outputClassName = outputMeta.getColumnName(i);
      // the MOJO API will provide list of target labels in the future
      // Link: https://github.com/h2oai/mojo2/issues/1366
      String[] outputClassNameSplit = outputClassName.split("\\.");
      String refinedOutputClass = outputClassNameSplit[outputClassNameSplit.length - 1 ];
      outputClass.add(refinedOutputClass);
    }
    return outputClass;
  }

  // note this will be provided by mojo pipeline in the future
  private ScoringType scoringType(int outputColumnSize) {
    if (outputColumnSize > 2) {
      return ScoringType.CLASSIFICATION;
    } else if (outputColumnSize == 2) {
      return ScoringType.BINOMIAL;
    } else {
      return ScoringType.REGRESSION;
    }
  }

  /**
   * Method to score a csv file on path provided as part of request {@link ScoreRequest} payload.
   *
   * @param csvFilePath {@link String} path to csv to score. MUST exist locally.
   * @return response {@link ScoreResponse}
   * @throws IOException if csv file does not exist on local path, IOException will be thrown
   */
  public ScoreResponse scoreCsv(String csvFilePath) throws IOException {
    MojoFrame requestFrame;
    try (InputStream csvStream = getInputStream(csvFilePath)) {
      requestFrame = csvConverter.apply(csvStream, pipeline.getInputFrameBuilder());
    }
    MojoFrame responseFrame = doScore(requestFrame);
    ScoreResponse response = scoreResponseConverter.apply(responseFrame, new ScoreRequest());
    response.id(pipeline.getUuid());
    return response;
  }

  private static InputStream getInputStream(String filePath) throws IOException {
    File csvFile = new File(filePath);
    if (!csvFile.isFile()) {
      throw new FileNotFoundException(
          String.format("Could not find the input CSV file: %s", filePath));
    }
    return new FileInputStream(filePath);
  }

  private static MojoFrame doScore(MojoFrame requestFrame) {
    log.debug(
        "Input has {} rows, {} columns: {}",
        requestFrame.getNrows(),
        requestFrame.getNcols(),
        Arrays.toString(requestFrame.getColumnNames()));
    MojoFrame responseFrame = pipeline.transform(requestFrame);
    log.debug(
        "Response has {} rows, {} columns: {}",
        responseFrame.getNrows(),
        responseFrame.getNcols(),
        Arrays.toString(responseFrame.getColumnNames()));
    return responseFrame;
  }

  /**
   * Method to get shapley contribution for an incoming request of type {@link ScoreRequest}.
   *
   * @param requestFrame {@link MojoFrame}
   * @param isOriginal {@link boolean} Simple boolean to specify if the shapley contribution
   *                                  has to be performed for original features
   *                                  or transformed features
   * @return response {@link MojoFrame}
   */
  private static MojoFrame doShapleyContrib(MojoFrame requestFrame, boolean isOriginal) {
    log.debug(
            "Input has {} rows, {} columns: {}",
            requestFrame.getNrows(),
            requestFrame.getNcols(),
            Arrays.toString(requestFrame.getColumnNames()));
    MojoFrame shapleyResponseFrame;
    if (isOriginal) {
      shapleyResponseFrame = pipelineOriginalShapley.transform(requestFrame);
    } else {
      shapleyResponseFrame = pipelineTransformedShapley.transform(requestFrame);
    }
    log.debug(
            "Response has {} rows, {} columns: {}",
            shapleyResponseFrame.getNrows(),
            shapleyResponseFrame.getNcols(),
            Arrays.toString(shapleyResponseFrame.getColumnNames()));
    return shapleyResponseFrame;
  }

  public String getModelId() {
    return pipeline.getUuid();
  }

  public MojoPipeline getPipeline() {
    return pipeline;
  }

  public Model getModelInfo() {
    return modelInfoConverter.apply(pipeline);
  }

  public ShapleyLoadOption getEnabledShapleyTypes() {
    return enabledShapleyTypes;
  }

  /**
   * Method to load mojo pipelines for shapley scoring based on configuration
   *
   * <p>Order of operations to preserve backwards compatibility:
   * 1. if property or env var shapley.types.enabled is set, load pipelines based on that
   * 2. if shapley.enabled is true load all pipelines
   *
   */
  private void loadMojoPipelinesForShapley() {
    if (ShapleyLoadOption.NONE == enabledShapleyTypes) {
      return;
    }
    switch (enabledShapleyTypes) {
      case ORIGINAL:
        log.info("Loading mojo for original shapley values");
        pipelineOriginalShapley = loadMojoPipelineFromFile();
        pipelineOriginalShapley.setShapPredictContribOriginal(true);
        break;
      case TRANSFORMED:
        log.info("Loading mojo for transformed shapley values.");
        pipelineTransformedShapley = loadMojoPipelineFromFile();
        pipelineTransformedShapley.setShapPredictContrib(true);
        break;
      case ALL:
        log.info("Loading mojo for all shapley value types.");
        pipelineTransformedShapley = loadMojoPipelineFromFile();
        pipelineTransformedShapley.setShapPredictContrib(true);

        pipelineOriginalShapley = loadMojoPipelineFromFile();
        pipelineOriginalShapley.setShapPredictContribOriginal(true);
        break;
      default:
        throw new IllegalArgumentException("Unexpected enabled shapley value type.");
    }
  }

  private static MojoPipeline loadMojoPipelineFromFile() {
    Preconditions.checkArgument(
        !Strings.isNullOrEmpty(MOJO_PIPELINE_PATH),
        "Path to mojo pipeline not specified, set the %s property.",
        MOJO_PIPELINE_PATH_PROPERTY);
    log.info("Loading Mojo pipeline from path {}", MOJO_PIPELINE_PATH);
    File mojoFile = new File(MOJO_PIPELINE_PATH);
    if (!mojoFile.isFile()) {
      ClassLoader classLoader = MojoScorer.class.getClassLoader();
      URL resourcePath = classLoader.getResource(MOJO_PIPELINE_PATH);
      if (resourcePath != null) {
        mojoFile = new File(resourcePath.getFile());
      }
    }
    if (!mojoFile.isFile()) {
      throw new RuntimeException("Could not load mojo from file: " + mojoFile);
    }
    try {
      MojoPipeline mojoPipeline = MojoPipelineService.loadPipeline(mojoFile);
      log.info("Mojo pipeline successfully loaded ({}).", mojoPipeline.getUuid());
      return mojoPipeline;
    } catch (IOException e) {
      throw new RuntimeException("Unable to load mojo from " + mojoFile, e);
    } catch (LicenseException e) {
      throw new RuntimeException("License file not found", e);
    }
  }
}
