package ai.h2o.mojos.deploy.common.transform;

import ai.h2o.mojos.deploy.common.rest.model.DataField;
import ai.h2o.mojos.deploy.common.rest.model.Row;
import ai.h2o.mojos.deploy.common.rest.model.ScoreRequest;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transform scoring request rows, specifically, convert boolean literal string into 1, 0
 * respectively.
 */
public class ScoreRequestTransformer implements BiConsumer<ScoreRequest, List<DataField>> {

  private static final Logger logger = LoggerFactory.getLogger(ScoreRequestTransformer.class);

  @Override
  public void accept(ScoreRequest scoreRequest, List<DataField> dataFields) {
    Map<String, DataField> dataFieldMap =
        dataFields.stream()
            .collect(ImmutableMap.toImmutableMap(DataField::getName, Function.identity()));
    scoreRequest.setRows(
        transformRow(scoreRequest.getFields(), scoreRequest.getRows(), dataFieldMap));
  }

  private List<Row> transformRow(
      List<String> fields, List<Row> rows, Map<String, DataField> dataFields) {
    return rows.stream().map(row -> IntStream.range(0, row.size()).mapToObj(
        fieldIdx -> {
          String colName = fields.get(fieldIdx);
          String origin = row.get(fieldIdx);
          if (dataFields.containsKey(colName)) {
            String sanitizeValue = Utils.sanitizeBoolean(
                origin, dataFields.get(colName).getDataType()
            );
            if (!sanitizeValue.equals(origin)) {
              logger.debug("Value '{}' parsed as '{}'", origin, sanitizeValue);
            }
            return sanitizeValue;
          } else {
            logger.warn("Column '{}' can not be found in Input schema", colName);
            return origin;
          }
        }
    ).collect(Collectors.toCollection(Row::new))).collect(Collectors.toList());
  }
}
