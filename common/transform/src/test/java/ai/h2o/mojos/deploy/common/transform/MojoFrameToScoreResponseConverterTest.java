package ai.h2o.mojos.deploy.common.transform;

import static ai.h2o.mojos.runtime.frame.MojoColumn.Kind;
import static ai.h2o.mojos.runtime.frame.MojoColumn.Type.Bool;
import static ai.h2o.mojos.runtime.frame.MojoColumn.Type.Float32;
import static ai.h2o.mojos.runtime.frame.MojoColumn.Type.Float64;
import static ai.h2o.mojos.runtime.frame.MojoColumn.Type.Int32;
import static ai.h2o.mojos.runtime.frame.MojoColumn.Type.Int64;
import static ai.h2o.mojos.runtime.frame.MojoColumn.Type.Str;
import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ai.h2o.mojos.deploy.common.rest.model.Row;
import ai.h2o.mojos.deploy.common.rest.model.ScoreRequest;
import ai.h2o.mojos.deploy.common.rest.model.ScoreResponse;
import ai.h2o.mojos.runtime.api.MojoColumnMeta;
import ai.h2o.mojos.runtime.frame.MojoColumn.Type;
import ai.h2o.mojos.runtime.frame.MojoFrame;
import ai.h2o.mojos.runtime.frame.MojoFrameBuilder;
import ai.h2o.mojos.runtime.frame.MojoFrameMeta;
import ai.h2o.mojos.runtime.frame.MojoRowBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MojoFrameToScoreResponseConverterTest {

  @Test
  void convertEmptyRowsResponse_succeeds() {
    // Given
    final MojoFrameToScoreResponseConverter converter
        = new MojoFrameToScoreResponseConverter();
    ScoreRequest scoreRequest = new ScoreRequest();
    MojoFrame mojoFrame =
        new MojoFrameBuilder(
                MojoFrameMeta.getEmpty(), Collections.emptyList(), Collections.emptyMap())
            .toMojoFrame();

    // When
    ScoreResponse result = converter.apply(mojoFrame, scoreRequest);

    // Then
    assertThat(result.getScore()).isEmpty();
    assertThat(result.getFields()).isEmpty();
  }

  @Test
  void convertSingleFieldResponse_succeeds() {
    // Given
    final MojoFrameToScoreResponseConverter converter
        = new MojoFrameToScoreResponseConverter();
    String[] fields = {"field"};
    Type[] types = {Str};
    String[][] values = {{"value"}};
    ScoreRequest scoreRequest = new ScoreRequest();

    // When
    ScoreResponse result = converter.apply(
        buildMojoFrame(fields, types, values), scoreRequest);

    // Then
    assertThat(result.getScore()).containsExactly(asRow("value"));
    assertThat(result.getFields()).containsExactly("field");
  }

  @Test
  void convertSingleFieldResponse_withoutFieldNames_succeeds() {
    // Given
    final MojoFrameToScoreResponseConverter converter
        = new MojoFrameToScoreResponseConverter();
    String[] fields = {"field"};
    Type[] types = {Str};
    String[][] values = {{"value"}};
    ScoreRequest scoreRequest = new ScoreRequest();
    scoreRequest.setNoFieldNamesInOutput(true);

    // When
    ScoreResponse result = converter.apply(
        buildMojoFrame(fields, types, values), scoreRequest);

    // Then
    assertThat(result.getScore()).containsExactly(asRow("value"));
    assertThat(result.getFields()).isNull();
  }

  @Test
  void convertIncludesOneField_succeeds() {
    // Given
    final MojoFrameToScoreResponseConverter converter
        = new MojoFrameToScoreResponseConverter();
    String[] fields = {"outputField"};
    Type[] types = {Str};
    String[][] values = {{"outputValue"}};
    ScoreRequest scoreRequest = new ScoreRequest();
    scoreRequest.addFieldsItem("inputField");
    scoreRequest.addIncludeFieldsInOutputItem("inputField");
    scoreRequest.addRowsItem(asRow("inputValue"));

    // When
    ScoreResponse result = converter.apply(
        buildMojoFrame(fields, types, values), scoreRequest);

    // Then
    assertThat(result.getScore()).containsExactly(asRow("inputValue", "outputValue"));
    assertThat(result.getFields()).containsExactly("inputField", "outputField").inOrder();
  }

  @Test
  void convertIncludesSomeFields_succeeds() {
    // Given
    final MojoFrameToScoreResponseConverter converter
        = new MojoFrameToScoreResponseConverter();
    String[] fields = {"outputField1", "outputField2"};
    Type[] types = {Str, Str};
    String[][] values = {{"outputValue1", "outputValue2"}};
    ScoreRequest scoreRequest = new ScoreRequest();
    scoreRequest.setFields(asList("inputField1", "inputField2", "inputField3"));
    scoreRequest.setIncludeFieldsInOutput(asList("inputField1", "inputField3"));
    scoreRequest.addRowsItem(asRow("inputValue1", "omittedValue", "inputValue3"));

    // When
    ScoreResponse result = converter.apply(
        buildMojoFrame(fields, types, values), scoreRequest);

    // Then
    assertThat(result.getScore())
        .containsExactly(asRow("inputValue1", "inputValue3", "outputValue1", "outputValue2"));
    assertThat(result.getFields())
        .containsExactly("inputField1", "inputField3", "outputField1", "outputField2")
        .inOrder();
  }

  @Test
  void convertIncludePresentIdField_succeeds() {
    // Given
    final MojoFrameToScoreResponseConverter converter
        = new MojoFrameToScoreResponseConverter();
    String[] fields = {"outputField"};
    Type[] types = {Str};
    String[][] values = {{"outputValue"}};
    ScoreRequest scoreRequest = new ScoreRequest();
    scoreRequest.setFields(asList("inputField", "omittedField", "id"));
    scoreRequest.setIncludeFieldsInOutput(asList("inputField", "id"));
    scoreRequest.addRowsItem(asRow("inputValue", "omittedValue", "testId"));
    scoreRequest.setIdField("id");

    // When
    ScoreResponse result = converter.apply(
        buildMojoFrame(fields, types, values), scoreRequest);

    // Then
    assertThat(result.getScore()).containsExactly(asRow("inputValue", "testId", "outputValue"));
    assertThat(result.getFields()).containsExactly("inputField", "id", "outputField").inOrder();
  }

  @Test
  void convertIncludeMissingIdField_generateUuid() {
    // Given
    final MojoFrameToScoreResponseConverter converter
        = new MojoFrameToScoreResponseConverter();
    String[] fields = {"outputField"};
    Type[] types = {Str};
    String[][] values = {{"outputValue"}};
    ScoreRequest scoreRequest = new ScoreRequest();
    scoreRequest.setFields(asList("inputField", "omittedField"));
    scoreRequest.setIncludeFieldsInOutput(asList("inputField", "id"));
    scoreRequest.addRowsItem(asRow("inputValue", "omittedValue", "testId"));
    scoreRequest.setIdField("id");

    // When
    ScoreResponse result = converter.apply(
        buildMojoFrame(fields, types, values), scoreRequest);

    // Then
    assertThat(result.getScore()).hasSize(1);
    assertThat(result.getScore().get(0)).hasSize(3);
    assertThat(result.getScore().get(0).get(0)).isEqualTo("inputValue");
    assertThat(result.getScore().get(0).get(1))
        .matches(
            "[0-9a-fA-F]{8}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{12}");
    assertThat(result.getScore().get(0).get(2)).isEqualTo("outputValue");
    assertThat(result.getFields()).containsExactly("inputField", "id", "outputField").inOrder();
  }

  @Test
  void convertMoreRowsResponse_succeeds() {
    // Given
    final MojoFrameToScoreResponseConverter converter
        = new MojoFrameToScoreResponseConverter();
    String[] fields = {"field"};
    Type[] types = {Str};
    String[][] values = {{"value1"}, {"value2"}, {"value3"}};
    ScoreRequest scoreRequest = new ScoreRequest();

    // When
    ScoreResponse result = converter.apply(
        buildMojoFrame(fields, types, values), scoreRequest);

    // Then
    assertThat(result.getScore())
        .containsExactly(Stream.of(values)
                .map(MojoFrameToScoreResponseConverterTest::asRow).toArray())
        .inOrder();
    assertThat(result.getFields()).containsExactly("field");
  }

  @Test
  void convertMoreTypesResponse_MojoColumnsDifferSchema_fails() {
    // Given
    final MojoFrameToScoreResponseConverter converter
        = new MojoFrameToScoreResponseConverter(true, Collections.singletonList("not_exists"));
    Type[] types = {Float64, Float64, Float64};
    Double[][] values = new Double[][]{{3.5, -1.0, 2.0}, {3.3, 11.9, 10.3}};
    ScoreRequest scoreRequest = new ScoreRequest();

    // When & Then
    assertThrows(IllegalArgumentException.class, () -> {
      converter.apply(
          buildMojoFrame(
            new String[]{"result.upper", "result", "result.lower"},
            types, values, MojoFrameToScoreResponseConverterTest::setJavaValue),
          scoreRequest);
    });
  }

  @ParameterizedTest
  @MethodSource("provideValues_convertMoreTypesResponse_succeeds")
  void convertMoreTypesResponse_succeeds(String[][] values) {
    // Given
    final MojoFrameToScoreResponseConverter converter
        = new MojoFrameToScoreResponseConverter();
    Type[] types = {Str, Float32, Float64, Bool, Int32, Int64};
    ScoreRequest scoreRequest = new ScoreRequest();

    // When
    ScoreResponse result =
        converter.apply(
        buildMojoFrame(
          Stream.of(types).map(Object::toString).toArray(String[]::new), types, values),
        scoreRequest);

    // Then
    assertThat(result.getScore())
        .containsExactly(Stream.of(values)
        .map(MojoFrameToScoreResponseConverterTest::asRow).toArray());
    assertThat(result.getFields())
      .containsExactly("Str", "Float32", "Float64", "Bool", "Int32", "Int64")
      .inOrder();
  }

  @ParameterizedTest
  @MethodSource("provideValues_convertMoreTypesResponse_actualValues_succeeds")
  void convertMoreTypesResponse_actualValues_succeeds(Object[][] values, String[][] expValues) {
    // Given
    final MojoFrameToScoreResponseConverter converter
        = new MojoFrameToScoreResponseConverter();
    Type[] types = {Str, Float32, Float64, Bool, Int32, Int64};
    ScoreRequest scoreRequest = new ScoreRequest();

    // When
    ScoreResponse result =
        converter.apply(
            buildMojoFrame(
                Stream.of(types).map(Object::toString).toArray(String[]::new),
                types,
                values,
              MojoFrameToScoreResponseConverterTest::setJavaValue),
            scoreRequest);

    // Then
    assertThat(result.getScore())
        .containsExactly(
            Stream.of(expValues).map(MojoFrameToScoreResponseConverterTest::asRow).toArray());
    assertThat(result.getFields())
        .containsExactly("Str", "Float32", "Float64", "Bool", "Int32", "Int64")
        .inOrder();
  }

  @ParameterizedTest
  @MethodSource("provideValues_predictionIntervalEnabledResponse_succeeds")
  void convertMoreTypesResponse_enablePredictionIntervalSameType_succeeds(
      String[][] expPredictionIntervalValues, String[][] expValues, ScoreRequest scoreRequest,
      MojoFrame mojoFrame, Object[] expectFields, Object[] expectPredictionIntervalFields) {
    // Given
    final MojoFrameToScoreResponseConverter converter
        = new MojoFrameToScoreResponseConverter(true, Collections.emptyList());

    // When
    ScoreResponse result =
        converter.apply(mojoFrame, scoreRequest);

    // Then
    assertThat(result.getScore())
        .containsExactly(
          toRowArray(expValues));
    assertThat(result.getFields())
      .containsExactly(expectFields)
      .inOrder();
    if (expectPredictionIntervalFields.length > 0) {
      assertThat(result.getPredictionIntervals().getFields())
        .containsExactly(expectPredictionIntervalFields)
        .inOrder();
      assertThat(result.getPredictionIntervals().getRows())
          .containsExactly(
          toRowArray(expPredictionIntervalValues));
    } else {
      assertThat(result.getPredictionIntervals())
        .isNull();
    }
  }

  @ParameterizedTest
  @MethodSource("provideValue_predictionIntervalEnabledResponse_fails")
  void convertMoreTypesResponse_NotSupportPredictionInterval_fails(
      Object[][] values, Type[] types) {
    // Given
    final MojoFrameToScoreResponseConverter converter
        = new MojoFrameToScoreResponseConverter(false, Collections.emptyList());
    ScoreRequest scoreRequest = new ScoreRequest();
    scoreRequest.requestPredictionIntervals(true);

    // When & Then
    assertThrows(IllegalArgumentException.class, () -> {
      converter.apply(
          buildMojoFrame(
          Stream.of(types).map(Object::toString).toArray(String[]::new),
          types, values, MojoFrameToScoreResponseConverterTest::setJavaValue),
          scoreRequest);
    });
  }

  @ParameterizedTest
  @MethodSource("provideValue_predictionIntervalEnabledResponse_fails")
  void convertMoreTypesResponse_disablePredictionIntervalNotSupportDiffType_succeeds(
      Object[][] values, Type[] types) {
    // Given
    final MojoFrameToScoreResponseConverter converter
        = new MojoFrameToScoreResponseConverter();
    ScoreRequest scoreRequest = new ScoreRequest();

    // When
    ScoreResponse result =
        converter.apply(
        buildMojoFrame(
          Stream.of(types).map(Object::toString).toArray(String[]::new),
          types, values, MojoFrameToScoreResponseConverterTest::setJavaValue),
        scoreRequest);

    // Then
    assertThat(result.getFields())
      .containsExactly(Stream.of(types).map(Object::toString).toArray())
      .inOrder();
    assertThat(result.getPredictionIntervals())
      .isNull();
  }

  private static Stream<Arguments> provideValues_predictionIntervalEnabledResponse_succeeds() {
    ScoreRequest[] requests = new ScoreRequest[]{new ScoreRequest(), new ScoreRequest()};
    Double[][][] values = new Double[][][]{
      {{3.5, 2.0, -1.0}, {11.9, 10.3, 3.3}},{{5.9, 3.4, 2.7}, {3.3, 2.2, 1.1}}
    };
    String[][][] expPredictionIntervalValues = new String[][][]{
      {{"3.5", "-1.0"},{"11.9","3.3"}},{{}}
    };
    String[][][] expValues = new String[][][]{
      {{"3.5", "2.0", "-1.0"},{"11.9", "10.3", "3.3"}},{{"3.4"},{"2.2"}}
    };
    Type[] types = {Float64, Float64, Float64};
    String[] fields = new String[]{"result.upper", "result", "result.lower"};
    String[][] expFields = new String[][]{{"result.upper", "result", "result.lower"}, {"result"}};
    String[][] predictionFields = new String[][]{{"result.upper", "result.lower"}, {}};

    MojoFrame[] mojoFrames = new MojoFrame[]{
      buildMojoFrame(
        fields, types, values[0], MojoFrameToScoreResponseConverterTest::setJavaValue),
        buildMojoFrame(
          fields, types, values[1], MojoFrameToScoreResponseConverterTest::setJavaValue)};
    requests[0].setRequestPredictionIntervals(true);
    return Stream.of(
      Arguments.of(
        expPredictionIntervalValues[0],
        expValues[0],
        requests[0],
        mojoFrames[0],
        expFields[0],
        predictionFields[0]),
      Arguments.of(
        expPredictionIntervalValues[1],
        expValues[1],
        requests[1],
        mojoFrames[1],
        expFields[1],
        predictionFields[1])
    );
  }

  private static Stream<Arguments> provideValues_predictionIntervalDiffSchema_succeeds() {
    return Stream.of(
      Arguments.of(
        new Double[][]{{3.5, -1.0, 2.0}, {3.3, 11.9, 10.3}},
        new String[][]{{"3.5", "2.0"}, {"3.3", "10.3"}}),
      Arguments.of(
        new Double[][]{{2.7, 3.4, 5.9}, {1.1, 2.2, 3.3}},
        new String[][]{{"2.7", "5.9"}, {"1.1", "3.3"}})
    );
  }

  private static Stream<Arguments> provideValue_predictionIntervalEnabledResponse_fails() {
    return Stream.of(
      Arguments.of(new Double[][]{{12.2, 11.221},{1.1, 99.1}}, new Type[]{Float64, Float64}),
      Arguments.of(new Double[][]{{10.1}, {121.1}}, new Type[]{Float64}),
      Arguments.of(new Double[][]{}, new Type[]{}),
      Arguments.of(new Object[][]{
        {"abc", null, 12}, {"bbc", 12.4f, 15}}, new Type[]{Str, Float32, Int32}),
      Arguments.of(new Object[][]{
        {90L, 1.21f, 12}, {11L, 12.4f, 15}}, new Type[]{Int64, Float32, Int32}),
      Arguments.of(new Object[][]{
        {false, true, false}, {true, null, false}}, new Type[]{Bool, Bool, Bool})
    );
  }

  @SuppressWarnings("unused")
  private static Stream<Arguments> provideValues_convertMoreTypesResponse_succeeds() {
    return Stream.of(
      Arguments.of((Object) new String[][] {{"str", "1.1", "2.2", "1", "123", "123456789"}}),
      Arguments.of((Object) new String[][] {{null, null, null, null, null, null}}));
  }

  @SuppressWarnings("unused")
  private static Stream<Arguments> provideValues_convertMoreTypesResponse_actualValues_succeeds() {
    return Stream.of(
        Arguments.of(
            aao("foo", -1.0f, -2.0, true, 1, 2L), aas("foo", "-1.0", "-2.0", "1", "1", "2")),
        Arguments.of(
            aao(null, Float.NaN, Double.NaN, null, null, null),
            aas(null, null, null, null, null, null)),
        Arguments.of(
            aao(
                null,
                Float.NEGATIVE_INFINITY,
                Double.NEGATIVE_INFINITY,
                null,
                Integer.MIN_VALUE,
                Long.MIN_VALUE),
            aas(null, "-Infinity", "-Infinity", null, null, null)),
        Arguments.of(
            aao(
                null,
                Float.POSITIVE_INFINITY,
                Double.POSITIVE_INFINITY,
                null,
                Integer.MAX_VALUE,
                Long.MAX_VALUE),
            aas(null, "Infinity", "Infinity", null, "2147483647", "9223372036854775807")));
  }

  private static MojoFrame buildMojoFrame(String[] fields, Type[] types, String[][] values) {
    return buildMojoFrame(fields, types, values, (rb, type, col, value) -> rb.setValue(col, value));
  }

  private static <T> MojoFrame buildMojoFrame(
      String[] fields, Type[] types, T[][] values, RowBuilderSetter<T> setter) {
    final List<MojoColumnMeta> columns = MojoColumnMeta.toColumns(fields, types, Kind.Output);
    final MojoFrameMeta meta = new MojoFrameMeta(columns);
    final MojoFrameBuilder frameBuilder =
        new MojoFrameBuilder(meta, Collections.emptyList(), Collections.emptyMap());
    for (T[] row : values) {
      MojoRowBuilder rowBuilder = frameBuilder.getMojoRowBuilder();
      int col = 0;
      for (T value : row) {
        Type type = types[col];
        setter.setValue(rowBuilder, type, col++, value);
      }
      frameBuilder.addRow(rowBuilder);
    }
    return frameBuilder.toMojoFrame();
  }

  private static Row asRow(String... values) {
    Row row = new Row();
    row.ensureCapacity(values.length);
    row.addAll(asList(values));
    return row;
  }

  private static Row asRow(List<String> values) {
    Row row = new Row();
    row.ensureCapacity(values.size());
    row.addAll(values);
    return row;
  }

  private static Object[][] aao(Object... values) {
    return new Object[][] {values};
  }

  private static String[][] aas(String... values) {
    return new String[][] {values};
  }

  private static MojoRowBuilder setJavaValue(MojoRowBuilder rb, Type type, int col, Object value) {
    switch (type) {
      case Bool:
        return rb.setBool(col, (Boolean) value);
      case Str:
        return rb.setString(col, (String) value);
      case Int32:
        return rb.setInt(col, (Integer) value);
      case Int64:
        return rb.setLong(col, (Long) value);
      case Float32:
        return rb.setFloat(col, (Float) value);
      case Float64:
        return rb.setDouble(col, (Double) value);
      default:
        throw new IllegalArgumentException("Unsupported type " + type);
    }
  }

  private static Object[] toRowArray(String[][] expValues) {
    return Stream.of(expValues)
      .map(input -> {
        List<String> intervalRow = new ArrayList<>(input.length);
        for (String in : input) {
          intervalRow.add(in);
        }
        return intervalRow;
      })
      .map(MojoFrameToScoreResponseConverterTest::asRow).toArray();
  }

  @FunctionalInterface
  interface RowBuilderSetter<T> {
    void setValue(MojoRowBuilder rb, Type colType, int col, T value);
  }
}
