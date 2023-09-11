package ai.h2o.mojos.deploy.local.rest.filter;

import ai.h2o.mojos.deploy.local.rest.logging.ScorerJsonLayout;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;
import java.util.UUID;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

@Component
public class MdcLoggingFilter extends OncePerRequestFilter {
  private static final String MODEL_ID = "MODEL_ID";
  private static final String SHAPLEY_TYPE = "SHAPLEY_TYPES_ENABLED";
  private static final Logger log = LoggerFactory.getLogger(MdcLoggingFilter.class);

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain
  ) throws ServletException, IOException {
    try (MDC.MDCCloseable endpoint =
          MDC.putCloseable(ScorerJsonLayout.ENDPOINT, request.getServletPath());
        MDC.MDCCloseable requestType =
            MDC.putCloseable(ScorerJsonLayout.REQUEST_TYPE, request.getMethod());
        MDC.MDCCloseable modelId = MDC.putCloseable(
            ScorerJsonLayout.EXPERIMENT_ID, getFromEnvironment(MODEL_ID));
        MDC.MDCCloseable scorerType =
            MDC.putCloseable(ScorerJsonLayout.SCORER_TYPE, getScorerType());
        MDC.MDCCloseable requestId =
            MDC.putCloseable(ScorerJsonLayout.REQUEST_ID, UUID.randomUUID().toString());) {
      filterChain.doFilter(request, response);
      if (HttpServletResponse.SC_OK == response.getStatus()) {
        try (MDC.MDCCloseable responseCode = MDC.putCloseable(
            ScorerJsonLayout.RESPONSE_CODE, String.valueOf(response.getStatus()));) {
          log.info("Request finished successfully");
        }
      }
    } catch (Exception e) {
      try (MDC.MDCCloseable error = MDC.putCloseable(
          ScorerJsonLayout.ERROR, stringifyStackTrace(e));) {
        Optional<MDC.MDCCloseable> responseCode = e instanceof ResponseStatusException
            ? Optional.of(MDC.putCloseable(
            ScorerJsonLayout.RESPONSE_CODE,
            String.valueOf(((ResponseStatusException) e).getRawStatusCode())))
            : Optional.empty();
        log.error(e.getMessage());
        responseCode.ifPresent(MDC.MDCCloseable::close);
      }
      throw e;
    }
  }

  private String stringifyStackTrace(Throwable e) {
    StringWriter writer = new StringWriter();
    PrintWriter printWriter = new PrintWriter(writer);
    e.printStackTrace(printWriter);
    return writer.toString();
  }

  private String getScorerType() {
    String shapleyType = getFromEnvironment(SHAPLEY_TYPE);
    return String.format(
      "JAVA MOJO Scorer (%s)",
      shapleyType.isEmpty() ? "SHAPLEY DISABLED" : String.format("%s SHAPLEY", shapleyType));
  }

  private String getFromEnvironment(String key) {
    try {
      String value = System.getenv(key);
      return value == null ? "" : value;
    } catch (Exception ignored) {
      return "";
    }
  }
}
