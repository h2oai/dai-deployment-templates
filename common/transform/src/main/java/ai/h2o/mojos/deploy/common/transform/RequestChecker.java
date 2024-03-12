package ai.h2o.mojos.deploy.common.transform;

import static java.util.Arrays.asList;

import ai.h2o.mojos.deploy.common.rest.model.Row;
import ai.h2o.mojos.deploy.common.rest.model.ScoreRequest;
import ai.h2o.mojos.runtime.frame.MojoFrameMeta;
import java.util.List;

/**
 * Checks that the request is of the correct form matching the corresponding mojo pipeline.
 */
public class RequestChecker {
  private final SampleRequestBuilder sampleRequestBuilder;

  public RequestChecker(SampleRequestBuilder sampleRequestBuilder) {
    this.sampleRequestBuilder = sampleRequestBuilder;
  }

  /**
   * Verify that the request is valid and matches the expected {@link MojoFrameMeta}.
   *
   * @throws ScoreRequestFormatException on any issues.
   */
  public void verify(ScoreRequest scoreRequest, MojoFrameMeta expectedMeta)
      throws ScoreRequestFormatException {
    String message = getProblemMessageOrNull(scoreRequest, expectedMeta);
    if (message == null) {
      return;
    }
    throw new ScoreRequestFormatException(message, sampleRequestBuilder.build(expectedMeta));
  }

  private String getProblemMessageOrNull(ScoreRequest scoreRequest, MojoFrameMeta expectedMeta) {
    if (scoreRequest == null) {
      return "Request cannot be empty";
    }
    List<String> fields = scoreRequest.getFields();
    if (fields == null || fields.isEmpty()) {
      return "List of input fields cannot be empty";
    }
    List<Row> rows = scoreRequest.getRows();
    if (rows == null || rows.isEmpty()) {
      return "List of input data rows cannot be empty";
    }
    List<String> expectedFields = asList(expectedMeta.getColumnNames());
    if (!fields.containsAll(expectedFields)) {
      return String.format(
          "Input fields don't contain all the Mojo fields, expected %s actual %s",
          expectedFields, fields);
    }
    int i = 0;
    for (List<String> row : scoreRequest.getRows()) {
      if (row.size() != fields.size()) {
        return String.format("Not enough elements in row %d (zero-indexed)", i);
      }
      i++;
    }
    return null;
  }
}
