package ai.h2o.mojos.deploy.common.transform;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import ai.h2o.mojos.deploy.common.rest.model.ContributionRequest;
import ai.h2o.mojos.deploy.common.rest.model.Row;
import ai.h2o.mojos.deploy.common.rest.model.ScoreRequest;
import ai.h2o.mojos.deploy.common.rest.model.ScoreResponse;
import ai.h2o.mojos.deploy.common.rest.model.ShapleyType;
import ai.h2o.mojos.runtime.MojoPipeline;
import ai.h2o.mojos.runtime.api.BasePipelineListener;
import ai.h2o.mojos.runtime.api.MojoColumnMeta;
import ai.h2o.mojos.runtime.api.MojoPipelineService;
import ai.h2o.mojos.runtime.frame.MojoColumn;
import ai.h2o.mojos.runtime.frame.MojoFrame;
import ai.h2o.mojos.runtime.frame.MojoFrameBuilder;
import ai.h2o.mojos.runtime.frame.MojoFrameMeta;
import ai.h2o.mojos.runtime.frame.MojoRowBuilder;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MojoScorerTest {
  private static final String MOJO_PIPELINE_PATH = "src/test/resources/multinomial-pipeline.mojo";
  private static final String TEST_UUID = "TEST_UUID";

  @Mock private ScoreRequestToMojoFrameConverter scoreRequestConverter;
  @Mock private MojoFrameToScoreResponseConverter scoreResponseConverter;
  @Mock private MojoFrameToContributionResponseConverter contributionResponseConverter;
  @Mock private ContributionRequestToMojoFrameConverter contributionRequestConverter;
  @Mock private MojoPipelineToModelInfoConverter modelInfoConverter;
  @Mock private CsvToMojoFrameConverter csvConverter;

  @BeforeAll
  static void setup() {
    System.setProperty("mojo.path", MOJO_PIPELINE_PATH);
    mockDummyPipeline();
  }

  private static void mockDummyPipeline() {
    MojoPipeline dummyPipeline =
            new DummyPipeline(TEST_UUID, MojoFrameMeta.getEmpty(), MojoFrameMeta.getEmpty());
    MockedStatic<MojoPipelineService> theMock = Mockito.mockStatic(MojoPipelineService.class);
    theMock.when(() -> MojoPipelineService
            .loadPipeline(new File(MOJO_PIPELINE_PATH))).thenReturn(dummyPipeline);
  }

  @AfterAll
  static void clear() {
    // reset properties
    System.clearProperty("mojo.path");
    System.clearProperty("shapley.enable");
    System.clearProperty("shapley.types.enabled");
  }

  @AfterEach
  void clearShapleyProperties() {
    System.clearProperty("shapley.enable");
    System.clearProperty("shapley.types.enabled");
  }

  @Test
  void verifyScoreRequestWithoutShapley_ShapleyDisabled_Succeeds() {
    // Given
    ScoreRequest request = new ScoreRequest();
    request.addFieldsItem("field1");
    request.addRowsItem(toRow("text"));
    MojoFrame dummyMojoFrame = generateDummyTransformedMojoFrame();
    given(scoreRequestConverter.apply(any(), any()))
            .willReturn(dummyMojoFrame);
    ScoreResponse dummyResponse = generateDummyResponse();
    given(scoreResponseConverter.apply(any(), any()))
            .willReturn(dummyResponse);

    MojoScorer scorer = dummyScorer();

    // When & Then
    assertDoesNotThrow(() -> scorer.score(request));
  }

  @Test
  void verifyScoreRequestWithTransformedShapley_ShapleyDisabled_Fails() {
    // Given
    ScoreRequest request = new ScoreRequest();
    request.addFieldsItem("field1");
    request.addRowsItem(toRow("text"));
    request.setRequestShapleyValueType(ShapleyType.TRANSFORMED);
    MojoFrame dummyMojoFrame = generateDummyTransformedMojoFrame();
    given(scoreRequestConverter.apply(any(), any()))
            .willReturn(dummyMojoFrame);
    ScoreResponse dummyResponse = generateDummyResponse();
    given(scoreResponseConverter.apply(any(), any()))
            .willReturn(dummyResponse);

    MojoScorer scorer = dummyScorer();

    // When & Then
    assertThrows(
            IllegalArgumentException.class, () -> scorer.score(request));
  }

  @Test
  void verifyScoreRequestWithOriginalShapley_ShapleyDisabled_Fails() {
    // Given
    ScoreRequest request = new ScoreRequest();
    request.addFieldsItem("field1");
    request.addRowsItem(toRow("text"));
    request.setRequestShapleyValueType(ShapleyType.TRANSFORMED);
    MojoFrame dummyMojoFrame = generateDummyTransformedMojoFrame();
    given(scoreRequestConverter.apply(any(), any()))
            .willReturn(dummyMojoFrame);
    ScoreResponse dummyResponse = generateDummyResponse();
    given(scoreResponseConverter.apply(any(), any()))
            .willReturn(dummyResponse);

    MojoScorer scorer = dummyScorer();

    // When & Then
    assertThrows(
            IllegalArgumentException.class, () -> scorer.score(request));
  }

  @Test
  void verifyContributionWithOriginalShapley_ShapleyDisabled_Fails() {
    // Given
    System.setProperty("shapley.enable", "false");
    ContributionRequest request = new ContributionRequest();
    request.addFieldsItem("field1");
    request.addRowsItem(toRow("text"));
    request.setRequestShapleyValueType(ShapleyType.ORIGINAL);

    MojoScorer scorer = dummyScorer();

    // When & Then
    assertThrows(IllegalArgumentException.class, () -> scorer
            .computeContribution(request));
  }

  @Test
  void verifyContributionWithTransformedShapley_ShapleyDisabled_Fails() {
    // Given
    ContributionRequest request = new ContributionRequest();
    request.addFieldsItem("field1");
    request.addRowsItem(toRow("text"));
    request.setRequestShapleyValueType(ShapleyType.TRANSFORMED);

    MojoScorer scorer = dummyScorer();

    // When & Then
    assertThrows(IllegalArgumentException.class, () -> scorer
            .computeContribution(request));
  }

  @Test
  void verifyScoreRequestWithoutShapley_ShapleyEnabled_Succeeds() {
    // Given
    System.setProperty("shapley.enable", "true");

    ScoreRequest request = new ScoreRequest();
    request.addFieldsItem("field1");
    request.addRowsItem(toRow("text"));
    MojoFrame dummyMojoFrame = generateDummyTransformedMojoFrame();
    given(scoreRequestConverter.apply(any(), any()))
        .willReturn(dummyMojoFrame);
    ScoreResponse dummyResponse = generateDummyResponse();
    given(scoreResponseConverter.apply(any(), any()))
        .willReturn(dummyResponse);

    MojoScorer scorer = dummyScorer();

    // When & Then
    assertDoesNotThrow(() -> scorer.score(request));
  }

  @Test
  void verifyScoreRequestWithTransformedShapley_ShapleyEnabled_Succeeds() {
    // Given
    System.setProperty("shapley.enable", "true");

    ScoreRequest request = new ScoreRequest();
    request.addFieldsItem("field1");
    request.addRowsItem(toRow("text"));
    request.setRequestShapleyValueType(ShapleyType.TRANSFORMED);
    MojoFrame dummyMojoFrame = generateDummyTransformedMojoFrame();
    given(scoreRequestConverter.apply(any(), any()))
        .willReturn(dummyMojoFrame);
    ScoreResponse dummyResponse = generateDummyResponse();
    given(scoreResponseConverter.apply(any(), any()))
        .willReturn(dummyResponse);

    MojoScorer scorer = dummyScorer();

    // When & Then
    assertDoesNotThrow(() -> scorer.score(request));
  }

  @Test
  void verifyScoreRequestWithOriginalShapley_ShapleyEnabled_Succeeds() {
    // Given
    System.setProperty("shapley.enable", "true");

    ScoreRequest request = new ScoreRequest();
    request.addFieldsItem("field1");
    request.addRowsItem(toRow("text"));
    request.setRequestShapleyValueType(ShapleyType.ORIGINAL);
    MojoFrame dummyMojoFrame = generateDummyTransformedMojoFrame();
    given(scoreRequestConverter.apply(any(), any()))
        .willReturn(dummyMojoFrame);
    ScoreResponse dummyResponse = generateDummyResponse();
    given(scoreResponseConverter.apply(any(), any()))
        .willReturn(dummyResponse);

    MojoScorer scorer = dummyScorer();

    // When & Then
    assertDoesNotThrow(() -> scorer.score(request));
  }

  @Test
  void verifyScoreRequestWithoutShapley_ShapleyOptionAll_Succeeds() {
    // Given
    System.setProperty("shapley.types.enabled", "ALL");

    ScoreRequest request = new ScoreRequest();
    request.addFieldsItem("field1");
    request.addRowsItem(toRow("text"));
    MojoFrame dummyMojoFrame = generateDummyTransformedMojoFrame();
    given(scoreRequestConverter.apply(any(), any()))
        .willReturn(dummyMojoFrame);
    ScoreResponse dummyResponse = generateDummyResponse();
    given(scoreResponseConverter.apply(any(), any()))
        .willReturn(dummyResponse);

    MojoScorer scorer = dummyScorer();

    // When & Then
    assertDoesNotThrow(() -> scorer.score(request));
  }

  @Test
  void verifyScoreRequestWithTransformedShapley_ShapleyOptionAll_Succeeds() {
    // Given
    System.setProperty("shapley.types.enabled", "ALL");

    ScoreRequest request = new ScoreRequest();
    request.addFieldsItem("field1");
    request.addRowsItem(toRow("text"));
    request.setRequestShapleyValueType(ShapleyType.TRANSFORMED);
    MojoFrame dummyMojoFrame = generateDummyTransformedMojoFrame();
    given(scoreRequestConverter.apply(any(), any()))
        .willReturn(dummyMojoFrame);
    ScoreResponse dummyResponse = generateDummyResponse();
    given(scoreResponseConverter.apply(any(), any()))
        .willReturn(dummyResponse);

    MojoScorer scorer = dummyScorer();

    // When & Then
    assertDoesNotThrow(() -> scorer.score(request));
  }

  @Test
  void verifyScoreRequestWithOriginalShapley_ShapleyOptionAll_Succeeds() {
    // Given
    System.setProperty("shapley.types.enabled", "ALL");

    ScoreRequest request = new ScoreRequest();
    request.addFieldsItem("field1");
    request.addRowsItem(toRow("text"));
    request.setRequestShapleyValueType(ShapleyType.ORIGINAL);
    MojoFrame dummyMojoFrame = generateDummyTransformedMojoFrame();
    given(scoreRequestConverter.apply(any(), any()))
        .willReturn(dummyMojoFrame);
    ScoreResponse dummyResponse = generateDummyResponse();
    given(scoreResponseConverter.apply(any(), any()))
        .willReturn(dummyResponse);

    MojoScorer scorer = dummyScorer();

    // When & Then
    assertDoesNotThrow(() -> scorer.score(request));
  }

  @Test
  void verifyScoreRequestWithoutShapley_ShapleyOptionTransformed_Succeeds() {
    // Given
    System.setProperty("shapley.types.enabled", "TRANSFORMED");

    ScoreRequest request = new ScoreRequest();
    request.addFieldsItem("field1");
    request.addRowsItem(toRow("text"));
    MojoFrame dummyMojoFrame = generateDummyTransformedMojoFrame();
    given(scoreRequestConverter.apply(any(), any()))
        .willReturn(dummyMojoFrame);
    ScoreResponse dummyResponse = generateDummyResponse();
    given(scoreResponseConverter.apply(any(), any()))
        .willReturn(dummyResponse);

    MojoScorer scorer = dummyScorer();

    // When & Then
    assertDoesNotThrow(() -> scorer.score(request));
  }

  @Test
  void verifyScoreRequestWithTransformedShapley_ShapleyOptionTransformed_Succeeds() {
    // Given
    System.setProperty("shapley.types.enabled", "TRANSFORMED");

    ScoreRequest request = new ScoreRequest();
    request.addFieldsItem("field1");
    request.addRowsItem(toRow("text"));
    request.setRequestShapleyValueType(ShapleyType.TRANSFORMED);
    MojoFrame dummyMojoFrame = generateDummyTransformedMojoFrame();
    given(scoreRequestConverter.apply(any(), any()))
        .willReturn(dummyMojoFrame);
    ScoreResponse dummyResponse = generateDummyResponse();
    given(scoreResponseConverter.apply(any(), any()))
        .willReturn(dummyResponse);

    MojoScorer scorer = dummyScorer();

    // When & Then
    assertDoesNotThrow(() -> scorer.score(request));
  }

  @Test
  void verifyScoreRequestWithOriginalShapley_ShapleyOptionTransformed_Fails() {
    // Given
    System.setProperty("shapley.types.enabled", "TRANSFORMED");

    ScoreRequest request = new ScoreRequest();
    request.addFieldsItem("field1");
    request.addRowsItem(toRow("text"));
    request.setRequestShapleyValueType(ShapleyType.ORIGINAL);
    MojoFrame dummyMojoFrame = generateDummyTransformedMojoFrame();
    given(scoreRequestConverter.apply(any(), any()))
        .willReturn(dummyMojoFrame);
    ScoreResponse dummyResponse = generateDummyResponse();
    given(scoreResponseConverter.apply(any(), any()))
        .willReturn(dummyResponse);

    MojoScorer scorer = dummyScorer();

    // When & Then
    assertThrows(
        IllegalArgumentException.class, () -> scorer.score(request));
  }

  @Test
  void verifyScoreRequestWithoutShapley_ShapleyOptionOriginal_Succeeds() {
    // Given
    System.setProperty("shapley.types.enabled", "ORIGINAL");

    ScoreRequest request = new ScoreRequest();
    request.addFieldsItem("field1");
    request.addRowsItem(toRow("text"));
    MojoFrame dummyMojoFrame = generateDummyTransformedMojoFrame();
    given(scoreRequestConverter.apply(any(), any()))
        .willReturn(dummyMojoFrame);
    ScoreResponse dummyResponse = generateDummyResponse();
    given(scoreResponseConverter.apply(any(), any()))
        .willReturn(dummyResponse);

    MojoScorer scorer = dummyScorer();

    // When & Then
    assertDoesNotThrow(() -> scorer.score(request));
  }

  @Test
  void verifyScoreRequestWithTransformedShapley_ShapleyOptionOriginal_Fails() {
    // Given
    System.setProperty("shapley.types.enabled", "ORIGINAL");

    ScoreRequest request = new ScoreRequest();
    request.addFieldsItem("field1");
    request.addRowsItem(toRow("text"));
    request.setRequestShapleyValueType(ShapleyType.TRANSFORMED);
    MojoFrame dummyMojoFrame = generateDummyTransformedMojoFrame();
    given(scoreRequestConverter.apply(any(), any()))
        .willReturn(dummyMojoFrame);
    ScoreResponse dummyResponse = generateDummyResponse();
    given(scoreResponseConverter.apply(any(), any()))
        .willReturn(dummyResponse);

    MojoScorer scorer = dummyScorer();

    // When & Then
    assertThrows(
        IllegalArgumentException.class, () -> scorer.score(request));
  }

  @Test
  void verifyScoreRequestWithOriginalShapley_ShapleyOptionOriginal_Succeeds() {
    // Given
    System.setProperty("shapley.types.enabled", "ORIGINAL");

    ScoreRequest request = new ScoreRequest();
    request.addFieldsItem("field1");
    request.addRowsItem(toRow("text"));
    request.setRequestShapleyValueType(ShapleyType.ORIGINAL);
    MojoFrame dummyMojoFrame = generateDummyTransformedMojoFrame();
    given(scoreRequestConverter.apply(any(), any()))
        .willReturn(dummyMojoFrame);
    ScoreResponse dummyResponse = generateDummyResponse();
    given(scoreResponseConverter.apply(any(), any()))
        .willReturn(dummyResponse);

    MojoScorer scorer = dummyScorer();

    // When & Then
    assertDoesNotThrow(() -> scorer.score(request));
  }

  private static Row toRow(String... values) {
    Row row = new Row();
    row.addAll(Arrays.asList(values));
    return row;
  }

  private MojoFrame generateDummyTransformedMojoFrame() {
    final List<MojoColumnMeta> columns = new ArrayList<>();
    columns.add(MojoColumnMeta.newOutput("Prediction.0", MojoColumn.Type.Float64));
    final MojoFrameMeta meta = new MojoFrameMeta(columns);
    final MojoFrameBuilder frameBuilder =
            new MojoFrameBuilder(meta, Collections.emptyList(), Collections.emptyMap());
    MojoRowBuilder mojoRowBuilder = frameBuilder.getMojoRowBuilder();
    mojoRowBuilder.setValue("Prediction.0", "0.64");
    frameBuilder.addRow(mojoRowBuilder);
    mojoRowBuilder = frameBuilder.getMojoRowBuilder();
    mojoRowBuilder.setValue("Prediction.0", "0.13");
    frameBuilder.addRow(mojoRowBuilder);
    return frameBuilder.toMojoFrame();
  }

  private ScoreResponse generateDummyResponse() {
    ScoreResponse response = new ScoreResponse();
    List<Row> outputRows =
            Stream.generate(Row::new).limit(4).collect(Collectors.toList());
    response.setScore(outputRows);
    response.setFields(Arrays.asList("field1"));
    return response;
  }

  /** Dummy pipeline {@link MojoPipeline} just to mock the static methods used inside scoring. */
  private static class DummyPipeline extends MojoPipeline {
    private final MojoFrameMeta inputMeta;
    private final MojoFrameMeta outputMeta;

    private DummyPipeline(String uuid, MojoFrameMeta inputMeta, MojoFrameMeta outputMeta) {
      super(uuid, null, null);
      this.inputMeta = inputMeta;
      this.outputMeta = outputMeta;
    }

    static DummyPipeline ofMeta(MojoFrameMeta inputMeta, MojoFrameMeta outputMeta) {
      return new DummyPipeline(TEST_UUID, inputMeta, outputMeta);
    }

    @Override
    public MojoFrameMeta getInputMeta() {
      return inputMeta;
    }

    @Override
    public MojoFrameMeta getOutputMeta() {
      return outputMeta;
    }

    @Override
    public MojoFrameBuilder getOutputFrameBuilder(MojoFrameBuilder inputFrameBuilder) {
      return null;
    }

    @Override
    protected MojoFrameBuilder getFrameBuilder(MojoColumn.Kind kind) {
      return new MojoFrameBuilder(outputMeta);
    }

    @Override
    protected MojoFrameMeta getMeta(MojoColumn.Kind kind) {
      return outputMeta;
    }

    @Override
    public MojoFrame transform(MojoFrame inputFrame, MojoFrame outputFrame) {
      return outputFrame;
    }

    @Override
    public void setShapPredictContrib(boolean enable) {
    }

    @Override
    public void setShapPredictContribOriginal(boolean enable) {
    }

    @Override
    public void setListener(BasePipelineListener listener) {
    }

    @Override
    public void printPipelineInfo(PrintStream out) {

    }
  }

  public MojoScorer dummyScorer() {
    return new MojoScorer(
      scoreRequestConverter,
      scoreResponseConverter,
      contributionRequestConverter,
      contributionResponseConverter,
      modelInfoConverter,
      csvConverter
    );
  }
}
