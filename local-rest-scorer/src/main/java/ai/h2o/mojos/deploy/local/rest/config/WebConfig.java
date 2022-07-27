package ai.h2o.mojos.deploy.local.rest.config;

import ai.h2o.mojos.deploy.local.rest.converters.MultipartConverter;
import ai.h2o.mojos.deploy.local.rest.converters.ScoreMediaRequestConverter;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  @Override
  public void addFormatters(FormatterRegistry registry) {
    registry.addConverter(new MultipartConverter());
    registry.addConverter(new ScoreMediaRequestConverter());
  }
}
