package ai.h2o.mojos.deploy.common.transform;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import ai.h2o.mojos.deploy.common.rest.model.Row;
import ai.h2o.mojos.deploy.common.rest.model.ScoreRequest;
import ai.h2o.mojos.runtime.frame.MojoColumn;
import ai.h2o.mojos.runtime.frame.MojoFrameMeta;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RequestCheckerTest {
  private final ScoreRequest exampleRequest = new ScoreRequest();
  @Mock private SampleRequestBuilder sampleRequestBuilder;
  @InjectMocks private RequestChecker checker;

  @Test
  void verifyValidRequest_succeeds() throws ScoreRequestFormatException {
    // Given
    ScoreRequest request = new ScoreRequest();
    request.addFieldsItem("field1");
    request.addRowsItem(toRow("text"));
    MojoFrameMeta expectedMeta =
        new MojoFrameMeta(new String[] {"field1"}, new MojoColumn.Type[] {MojoColumn.Type.Str});

    // When
    checker.verify(request, expectedMeta);

    // Then all ok
  }

  @Test
  void verifyNullRequest_throws() {
    // Given
    ScoreRequest request = null;
    MojoFrameMeta expectedMeta = MojoFrameMeta.getEmpty();
    given(sampleRequestBuilder.build(any())).willReturn(exampleRequest);

    // When & then
    ScoreRequestFormatException exception =
        assertThrows(
            ScoreRequestFormatException.class, () -> checker.verify(request, expectedMeta));
    assertThat(exception.getMessage()).contains("empty");
    assertThat(exception.getExampleRequest()).isSameAs(exampleRequest);
  }

  @Test
  void verifyEmptyRequest_throws() {
    // Given
    ScoreRequest request = new ScoreRequest();
    MojoFrameMeta expectedMeta = MojoFrameMeta.getEmpty();
    given(sampleRequestBuilder.build(any())).willReturn(exampleRequest);

    // When & then
    ScoreRequestFormatException exception =
        assertThrows(
            ScoreRequestFormatException.class, () -> checker.verify(request, expectedMeta));
    assertThat(exception.getMessage()).contains("empty");
    assertThat(exception.getExampleRequest()).isSameAs(exampleRequest);
  }

  @Test
  void verifyEmptyFieldsRequest_throws() {
    // Given
    ScoreRequest request = new ScoreRequest();
    request.addRowsItem(toRow("text"));
    MojoFrameMeta expectedMeta =
        new MojoFrameMeta(new String[] {"field1"}, new MojoColumn.Type[] {MojoColumn.Type.Str});
    given(sampleRequestBuilder.build(any())).willReturn(exampleRequest);

    // When & then
    ScoreRequestFormatException exception =
        assertThrows(
            ScoreRequestFormatException.class, () -> checker.verify(request, expectedMeta));
    assertThat(exception.getMessage()).contains("fields cannot be empty");
    assertThat(exception.getExampleRequest()).isSameAs(exampleRequest);
  }

  @Test
  void verifyEmptyRowsRequest_throws() {
    // Given
    ScoreRequest request = new ScoreRequest();
    request.addFieldsItem("field1");
    MojoFrameMeta expectedMeta =
        new MojoFrameMeta(new String[] {"field1"}, new MojoColumn.Type[] {MojoColumn.Type.Str});
    given(sampleRequestBuilder.build(any())).willReturn(exampleRequest);

    // When & then
    ScoreRequestFormatException exception =
        assertThrows(
            ScoreRequestFormatException.class, () -> checker.verify(request, expectedMeta));
    assertThat(exception.getMessage()).contains("rows cannot be empty");
    assertThat(exception.getExampleRequest()).isSameAs(exampleRequest);
  }

  @Test
  void verifyMismatchedFieldsRequest_throws() {
    // Given
    ScoreRequest request = new ScoreRequest();
    request.addFieldsItem("a_fields");
    request.addRowsItem(toRow("text"));
    MojoFrameMeta expectedMeta =
        new MojoFrameMeta(
            new String[] {"different_fields"}, new MojoColumn.Type[] {MojoColumn.Type.Str});
    given(sampleRequestBuilder.build(any())).willReturn(exampleRequest);

    // When & then
    ScoreRequestFormatException exception =
        assertThrows(
            ScoreRequestFormatException.class, () -> checker.verify(request, expectedMeta));
    assertThat(exception.getMessage()).contains("fields don't contain all");
    assertThat(exception.getExampleRequest()).isSameAs(exampleRequest);
  }

  @Test
  void verifyTooFewFieldsRequest_throws() {
    // Given
    ScoreRequest request = new ScoreRequest();
    request.addFieldsItem("field1");
    request.addRowsItem(toRow("text"));
    MojoFrameMeta expectedMeta =
        new MojoFrameMeta(
            new String[] {"field1", "field2"},
            new MojoColumn.Type[] {MojoColumn.Type.Str, MojoColumn.Type.Str});
    given(sampleRequestBuilder.build(any())).willReturn(exampleRequest);

    // When & then
    ScoreRequestFormatException exception =
        assertThrows(
            ScoreRequestFormatException.class, () -> checker.verify(request, expectedMeta));
    assertThat(exception.getMessage()).contains("fields don't contain all");
    assertThat(exception.getExampleRequest()).isSameAs(exampleRequest);
  }

  @Test
  void verifyExtraFieldsRequest_succeeds() throws ScoreRequestFormatException {
    // Given
    ScoreRequest request = new ScoreRequest();
    request.addFieldsItem("field1");
    request.addFieldsItem("field2");
    request.addRowsItem(toRow("text1", "text2"));
    MojoFrameMeta expectedMeta =
        new MojoFrameMeta(new String[] {"field1"}, new MojoColumn.Type[] {MojoColumn.Type.Str});

    // When
    checker.verify(request, expectedMeta);

    // Then all ok
  }

  @Test
  void verifyMismatchedRowsRequest_throws() {
    // Given
    ScoreRequest request = new ScoreRequest();
    request.addFieldsItem("field1");
    request.addRowsItem(toRow("text"));
    request.addRowsItem(toRow("text", "additional text"));
    MojoFrameMeta expectedMeta =
        new MojoFrameMeta(new String[] {"field1"}, new MojoColumn.Type[] {MojoColumn.Type.Str});
    given(sampleRequestBuilder.build(any())).willReturn(exampleRequest);

    // When & then
    ScoreRequestFormatException exception =
        assertThrows(
            ScoreRequestFormatException.class, () -> checker.verify(request, expectedMeta));
    assertThat(exception.getMessage()).contains("row 1");
    assertThat(exception.getExampleRequest()).isSameAs(exampleRequest);
  }

  private static Row toRow(String... values) {
    Row row = new Row();
    row.addAll(Arrays.asList(values));
    return row;
  }
}
