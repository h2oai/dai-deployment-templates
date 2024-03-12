package ai.h2o.mojos.deploy.common.transform;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;

import ai.h2o.mojos.deploy.common.rest.model.PredictionInterval;
import ai.h2o.mojos.deploy.common.rest.model.Row;
import ai.h2o.mojos.deploy.common.rest.model.ScoreRequest;
import ai.h2o.mojos.deploy.common.rest.model.ScoreResponse;
import ai.h2o.mojos.runtime.frame.MojoFrame;
import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Converts the resulting predicted {@link MojoFrame} into the API response object {@link
 * ScoreResponse}.
 */
public class MojoFrameToScoreResponseConverter
    implements BiFunction<MojoFrame, ScoreRequest, ScoreResponse> {
  private static final Logger log =
      LoggerFactory.getLogger(MojoFrameToScoreResponseConverter.class);

  // If true then pipeline support prediction interval, otherwise false.
  // Note: assumption is that pipeline supports Prediction interval.
  // However for some h2o3 model, even classification model may still set
  // this to be true.
  private final Boolean supportPredictionInterval;

  public MojoFrameToScoreResponseConverter(boolean supportPredictionInterval) {
    this.supportPredictionInterval = supportPredictionInterval;
  }

  public MojoFrameToScoreResponseConverter() {
    supportPredictionInterval = false;
  }

  /**
   * Transform MOJO response frame into ScoreResponse.
   *
   * @param mojoFrame mojo response frame.
   * @param scoreRequest score request.
   * @return score response.
   */
  @Override
  public ScoreResponse apply(MojoFrame mojoFrame, ScoreRequest scoreRequest) {
    Set<String> includedFields = getSetOfIncludedFields(scoreRequest);
    List<Row> outputRows =
        Stream.generate(Row::new)
            .limit(mojoFrame.getNrows())
            .collect(Collectors.toList());
    copyFilteredInputFields(scoreRequest, includedFields, outputRows);
    fillOutputRows(mojoFrame, outputRows);

    ScoreResponse response = new ScoreResponse();
    response.setScore(outputRows);

    if (!Boolean.TRUE.equals(scoreRequest.getNoFieldNamesInOutput())) {
      List<String> outputFieldNames = getFilteredInputFieldNames(scoreRequest, includedFields);
      outputFieldNames.addAll(getTargetField(mojoFrame));
      response.setFields(outputFieldNames);
    }
    fillWithPredictionInterval(mojoFrame, scoreRequest, response);
    return response;
  }

  /**
   * Populate target column rows into outputRows. When prediction interval is returned from MOJO
   * response frame, only one column rows will be populated into the outputRows to ensure backward
   * compatible.
   */
  private void fillOutputRows(MojoFrame mojoFrame, List<Row> outputRows) {
    List<List<String>> targetRows = getTargetRows(mojoFrame);
    for (int rowIdx = 0; rowIdx < mojoFrame.getNrows(); rowIdx++) {
      outputRows.get(rowIdx).addAll(targetRows.get(rowIdx));
    }
  }

  /**
   * Populate Prediction Interval value into response field. Only when score request set
   * requestPredictionIntervals be true and MOJO pipeline support prediction interval.
   */
  private void fillWithPredictionInterval(
      MojoFrame mojoFrame, ScoreRequest scoreRequest, ScoreResponse scoreResponse) {
    if (Boolean.TRUE.equals(scoreRequest.getRequestPredictionIntervals())) {
      if (!supportPredictionInterval) {
        throw new IllegalStateException(
            "Unexpected error, prediction interval should be supported, but actually not");
      }
      PredictionInterval predictionInterval =
          new PredictionInterval().fields(new Row()).rows(Collections.emptyList());
      if (mojoFrame.getNcols() > 1) {
        int targetIdx = getTargetColIdx(Arrays.asList(mojoFrame.getColumnNames()));
        // Need to ensure target column is singular (regression).
        if (targetIdx >= 0) {
          predictionInterval.setFields(getPredictionIntervalFields(mojoFrame, targetIdx));
          predictionInterval.setRows(getPredictionIntervalRows(mojoFrame, targetIdx));
        }
      }
      scoreResponse.setPredictionIntervals(predictionInterval);
    }
  }

  /**
   * Extract target column rows from MOJO response frame. Note: To ensure backward compatibility, if
   * prediction interval is enabled then extracts only one column rows from response columns.
   */
  private List<List<String>> getTargetRows(MojoFrame mojoFrame) {
    List<List<String>> taretRows =
        Stream.generate(ArrayList<String>::new)
            .limit(mojoFrame.getNrows())
            .collect(Collectors.toList());
    for (int row = 0; row < mojoFrame.getNrows(); row++) {
      for (int col : getTargetFieldIndices(mojoFrame)) {
        String cell = mojoFrame.getColumn(col).getDataAsStrings()[row];
        taretRows.get(row).add(cell);
      }
    }
    return taretRows;
  }

  /**
   * Extract target columns from MOJO response frame. When prediction interval is enabled, extracts
   * only one column from MOJO frame, otherwise all columns names will be extracted.
   */
  private List<String> getTargetField(MojoFrame mojoFrame) {
    if (mojoFrame.getNcols() > 0) {
      List<String> targetColumns = Arrays.asList(mojoFrame.getColumnNames());
      if (supportPredictionInterval) {
        int targetIdx = getTargetColIdx(targetColumns);
        if (targetIdx < 0) {
          log.debug(
              "singular target column does not exist in MOJO response frame,"
                  + " this could be a classification model.");
        } else {
          return targetColumns.subList(targetIdx, targetIdx + 1);
        }
      }
      return targetColumns;
    } else {
      return Collections.emptyList();
    }
  }

  /**
   * Extract target columns indices from MOJO response frame. When prediction interval is enabled,
   * extracts only one column index from MOJO frame, otherwise all columns indices will be
   * extracted.
   */
  private List<Integer> getTargetFieldIndices(MojoFrame mojoFrame) {
    if (mojoFrame.getNcols() > 0) {
      List<String> targetColumns = Arrays.asList(mojoFrame.getColumnNames());
      if (supportPredictionInterval) {
        int targetIdx = getTargetColIdx(targetColumns);
        if (targetIdx < 0) {
          log.debug(
              "singular target column does not exist in MOJO response frame,"
                  + " this could be a classification model.");
        } else {
          return Collections.singletonList(targetIdx);
        }
      }
      return IntStream.range(0, mojoFrame.getNcols()).boxed().collect(Collectors.toList());
    } else {
      return Collections.emptyList();
    }
  }

  /**
   * Extract prediction interval columns rows from MOJO response frame. Note: Assumption is
   * prediction interval should already be enabled and response frame has expected structure.
   */
  private List<Row> getPredictionIntervalRows(MojoFrame mojoFrame, int targetIdx) {
    List<Row> predictionIntervalRows =
        Stream.generate(Row::new)
            .limit(mojoFrame.getNrows())
            .collect(Collectors.toList());
    for (int row = 0; row < mojoFrame.getNrows(); row++) {
      for (int col = 0; col < mojoFrame.getNcols(); col++) {
        if (col == targetIdx) {
          continue;
        }
        String cell = mojoFrame.getColumn(col).getDataAsStrings()[row];
        predictionIntervalRows.get(row).add(cell);
      }
    }
    return predictionIntervalRows;
  }

  /**
   * Extract prediction interval columns names from MOJO response frame. Note: Assumption is
   * prediction interval should already be enabled and response frame has expected structure.
   */
  private Row getPredictionIntervalFields(MojoFrame mojoFrame, int targetIdx) {
    Row row = new Row();
    List<String> mojoColumns = Arrays.asList(mojoFrame.getColumnNames());

    row.addAll(mojoColumns.subList(0, targetIdx));
    row.addAll(mojoColumns.subList(targetIdx + 1, mojoFrame.getNcols()));
    return row;
  }

  /**
   * Extract target column index from list of column names. Note: Assumption is a singular target
   * column should be found. Otherwise, the output indicates this a classification model.
   */
  private int getTargetColIdx(List<String> mojoColumns) {
    if (mojoColumns.size() == 1) {
      return 0;
    }
    String[] columns = mojoColumns.toArray(new String[0]);
    Arrays.sort(columns);
    StringBuilder builder = new StringBuilder();
    for (int idx = 0, cmpIdx = columns.length - 1; idx < columns[0].length(); idx++) {
      if (columns[0].charAt(idx) == columns[cmpIdx].charAt(idx)) {
        builder.append(columns[0].charAt(idx));
      } else {
        break;
      }
    }
    return mojoColumns.indexOf(builder.toString());
  }

  private static void copyFilteredInputFields(
      ScoreRequest scoreRequest, Set<String> includedFields, List<Row> outputRows) {
    if (includedFields.isEmpty()) {
      return;
    }
    boolean generateRowIds = shouldGenerateRowIds(scoreRequest, includedFields);
    List<Row> inputRows = scoreRequest.getRows();
    for (int row = 0; row < outputRows.size(); row++) {
      Row inputRow = inputRows.get(row);
      Row outputRow = outputRows.get(row);
      List<String> inputFields = scoreRequest.getFields();
      for (int col = 0; col < inputFields.size(); col++) {
        if (includedFields.contains(inputFields.get(col))) {
          outputRow.add(inputRow.get(col));
        }
      }
      if (generateRowIds) {
        outputRow.add(UUID.randomUUID().toString());
      }
    }
  }

  private static Set<String> getSetOfIncludedFields(ScoreRequest scoreRequest) {
    List<String> includedFields =
        Optional.ofNullable(scoreRequest.getIncludeFieldsInOutput()).orElse(emptyList());
    if (includedFields.isEmpty()) {
      return emptySet();
    }
    return new HashSet<>(includedFields);
  }

  private static boolean shouldGenerateRowIds(
      ScoreRequest scoreRequest, Set<String> includedFields) {
    String idField = scoreRequest.getIdField();
    return !Strings.isNullOrEmpty(idField)
        && includedFields.contains(idField)
        && !scoreRequest.getFields().contains(idField);
  }

  private static List<String> getFilteredInputFieldNames(
      ScoreRequest scoreRequest, Set<String> includedFields) {
    List<String> outputFields = new ArrayList<>();
    if (includedFields.isEmpty()) {
      return outputFields;
    }
    for (String field : scoreRequest.getFields()) {
      if (includedFields.contains(field)) {
        outputFields.add(field);
      }
    }
    if (shouldGenerateRowIds(scoreRequest, includedFields)) {
      outputFields.add(scoreRequest.getIdField());
    }
    return outputFields;
  }
}
