package ai.h2o.mojos.deploy.local.rest.exception;

import ai.h2o.mojos.deploy.common.exceptions.ScoringException;
import ai.h2o.mojos.deploy.common.exceptions.ScoringShapleyException;
import ai.h2o.mojos.deploy.local.rest.controller.ModelsApiController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@ControllerAdvice
public class ModelsApiExceptionController extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ModelsApiController.class);

  @ExceptionHandler(value = ScoringException.class)
  protected ResponseEntity<Object> handleScoringException(ScoringException scoringException) {
    log.info(scoringException.getMessage());
    log.debug(" - failure cause: ", scoringException);
    return new ResponseEntity<>(scoringException.getMessage(), HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(value = ScoringShapleyException.class)
  protected ResponseEntity<Object> handleScoringShapleyException(
      ScoringShapleyException scoringShapleyException) {
    log.info(scoringShapleyException.getMessage());
    log.debug(" - failure cause: ", scoringShapleyException);
    return new ResponseEntity<>(scoringShapleyException.getMessage(), HttpStatus.BAD_REQUEST);
  }
}
