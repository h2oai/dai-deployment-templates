package ai.h2o.mojos.deploy.local.rest.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.contrib.json.classic.JsonLayout;

import java.util.LinkedHashMap;
import java.util.Map;

public class ScorerJsonLayout extends JsonLayout {
  public static final String ENDPOINT = "Endpoint";
  public static final String ERROR = "Error";
  public static final String MESSAGE = "Message";
  public static final String EXPERIMENT_ID = "ExperimentId";
  public static final String NUM_ROWS = "NumberOfRows";
  public static final String REQUEST_TYPE = "RequestType";
  public static final String RESPONSE_CODE = "ResponseCode";
  public static final String SCORER_TYPE = "ScorerType";
  public static final String TIMESTAMP = "Timestamp";
  public static final String LOG_LEVEL = "LogLevel";
  public static final String REQUEST_ID = "RequestId";

  public ScorerJsonLayout() {
    super();
  }

  @Override
  protected Map toJsonMap(ILoggingEvent event) {
    Map<String, Object> map = new LinkedHashMap<>();

    addTimestamp(TIMESTAMP, includeTimestamp, event.getTimeStamp(), map);
    add(LOG_LEVEL, includeLevel, String.valueOf(event.getLevel()), map);
    add(MESSAGE, includeFormattedMessage, event.getFormattedMessage(), map);
    addThrowableInfo(ERROR, includeException, event, map);
    add(SCORER_TYPE, containsMdc(event, SCORER_TYPE), getFromMdc(event, SCORER_TYPE), map);
    add(ENDPOINT, containsMdc(event, ENDPOINT), getFromMdc(event, ENDPOINT), map);
    add(REQUEST_TYPE, containsMdc(event, REQUEST_TYPE), getFromMdc(event, REQUEST_TYPE), map);
    add(EXPERIMENT_ID, containsMdc(event, EXPERIMENT_ID), getFromMdc(event, EXPERIMENT_ID), map);
    add(NUM_ROWS, containsMdc(event, NUM_ROWS), getFromMdc(event, NUM_ROWS), map);
    add(RESPONSE_CODE, containsMdc(event, RESPONSE_CODE), getFromMdc(event, RESPONSE_CODE), map);
    add(REQUEST_ID, containsMdc(event, REQUEST_ID), getFromMdc(event, REQUEST_ID), map);
    addCustomDataToJsonMap(map, event);
    return map;
  }

  private String getFromMdc(ILoggingEvent event, String field) {
    return event.getMDCPropertyMap().getOrDefault(field, null);
  }

  private boolean containsMdc(ILoggingEvent event, String field) {
    return event.getMDCPropertyMap().containsKey(field)
      && !event.getMDCPropertyMap().get(field).isEmpty();
  }
}
