package ai.h2o.mojos.deploy.common.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ai.h2o.mojos.deploy.common.rest.model.DataField;
import ai.h2o.mojos.deploy.common.rest.model.Row;
import ai.h2o.mojos.deploy.common.rest.model.ScoreRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ScoreRequestTransformerTest {

  private final ScoreRequestTransformer scoreRequestTransformer = new ScoreRequestTransformer();

  @Test
  void transform_Empty_Empty() {
    // Given
    ScoreRequest scoreRequest = new ScoreRequest();
    scoreRequest.setFields(Collections.emptyList());
    scoreRequest.setRows(new ArrayList<>(Collections.emptyList()));
    DataField dataField = new DataField();
    dataField.setName("test");
    dataField.setDataType(DataField.DataTypeEnum.FLOAT32);
    List<DataField> dataFields = Collections.singletonList(dataField);

    // When
    scoreRequestTransformer.accept(scoreRequest, dataFields);

    // Then
    assertEquals(Collections.emptyList(), scoreRequest.getRows());
  }

  @Test
  void transform_BooleanLiteral_Transformed() {
    // Given
    ScoreRequest scoreRequest = new ScoreRequest();
    scoreRequest.setFields(Collections.singletonList("test"));
    List<Row> rows =
        new ArrayList<>(
            Arrays.asList(
                new Row(),
                new Row(),
                new Row(),
                new Row(),
                new Row(),
                new Row(),
                new Row()));
    rows.get(0).addAll(Collections.singletonList("true"));
    rows.get(1).addAll(Collections.singletonList("False"));
    rows.get(2).addAll(Collections.singletonList("TrUE"));
    rows.get(3).addAll(Collections.singletonList("FALse"));
    rows.get(4).addAll(Collections.singletonList("1"));
    rows.get(5).addAll(Collections.singletonList("0"));
    rows.get(6).addAll(Collections.singletonList("unchangedFeature"));
    scoreRequest.setRows(rows);
    DataField dataField = new DataField();
    dataField.setName("test");
    dataField.setDataType(DataField.DataTypeEnum.FLOAT32);
    List<DataField> dataFields = Collections.singletonList(dataField);

    // When
    scoreRequestTransformer.accept(scoreRequest, dataFields);

    // Then
    List<List<String>> expected =
        new ArrayList<>(
            Arrays.asList(
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>()));
    expected.get(0).addAll(Collections.singletonList("1"));
    expected.get(1).addAll(Collections.singletonList("0"));
    expected.get(2).addAll(Collections.singletonList("1"));
    expected.get(3).addAll(Collections.singletonList("0"));
    expected.get(4).addAll(Collections.singletonList("1"));
    expected.get(5).addAll(Collections.singletonList("0"));
    expected.get(6).addAll(Collections.singletonList("unchangedFeature"));
    assertEquals(expected, scoreRequest.getRows());
  }

  @Test
  void transform_NonBooleanLiteral_Unchanged() {
    // Given
    ScoreRequest scoreRequest = new ScoreRequest();
    scoreRequest.setFields(Collections.singletonList("test"));
    List<Row> rows = new ArrayList<>(Arrays.asList(new Row(), new Row()));
    rows.get(0).addAll(Collections.singletonList("unchangedFeature1"));
    rows.get(1).addAll(Collections.singletonList("unchangedFeature2"));
    scoreRequest.setRows(rows);
    DataField dataField = new DataField();
    dataField.setName("test");
    dataField.setDataType(DataField.DataTypeEnum.FLOAT64);
    List<DataField> dataFields = Collections.singletonList(dataField);

    // When
    scoreRequestTransformer.accept(scoreRequest, dataFields);

    // Then
    List<List<String>> expected =
        new ArrayList<>(Arrays.asList(new ArrayList<>(), new ArrayList<>()));
    expected.get(0).addAll(Collections.singletonList("unchangedFeature1"));
    expected.get(1).addAll(Collections.singletonList("unchangedFeature2"));
    assertEquals(expected, scoreRequest.getRows());
  }
}
