package ai.h2o.mojos.deploy.local.rest.config;

import ai.h2o.mojos.deploy.common.transform.ContributionRequestToMojoFrameConverter;
import ai.h2o.mojos.deploy.common.transform.CsvToMojoFrameConverter;
import ai.h2o.mojos.deploy.common.transform.MojoFrameToContributionResponseConverter;
import ai.h2o.mojos.deploy.common.transform.MojoFrameToScoreResponseConverter;
import ai.h2o.mojos.deploy.common.transform.MojoPipelineToModelInfoConverter;
import ai.h2o.mojos.deploy.common.transform.MojoScorer;
import ai.h2o.mojos.deploy.common.transform.SampleRequestBuilder;
import ai.h2o.mojos.deploy.common.transform.ScoreRequestToMojoFrameConverter;
import ai.h2o.mojos.deploy.common.transform.ScoreRequestTransformer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ScorerConfiguration {
  @Bean
  public MojoFrameToScoreResponseConverter responseConverter() {
    return new MojoFrameToScoreResponseConverter(
      MojoScorer.supportPredictionInterval,
      MojoScorer.modelOutputFieldNames);
  }

  @Bean
  public ScoreRequestToMojoFrameConverter requestConverter() {
    return new ScoreRequestToMojoFrameConverter();
  }

  @Bean
  public ContributionRequestToMojoFrameConverter contributionRequestConverter() {
    return new ContributionRequestToMojoFrameConverter();
  }

  @Bean
  public MojoFrameToContributionResponseConverter contributionResponseConverter() {
    return new MojoFrameToContributionResponseConverter();
  }

  @Bean
  public MojoPipelineToModelInfoConverter modelConverter() {
    return new MojoPipelineToModelInfoConverter();
  }

  @Bean
  public CsvToMojoFrameConverter csvConverter() {
    return new CsvToMojoFrameConverter();
  }

  @Bean
  public SampleRequestBuilder sampleRequestBuilder() {
    return new SampleRequestBuilder();
  }

  @Bean
  public ScoreRequestTransformer scoreRequestTransformer() {
    return new ScoreRequestTransformer();
  }

  @Bean
  public MojoScorer mojoScorer(
      ScoreRequestToMojoFrameConverter requestConverter,
      MojoFrameToScoreResponseConverter responseConverter,
      ContributionRequestToMojoFrameConverter contributionRequestConverter,
      MojoFrameToContributionResponseConverter contributionResponseConverter,
      MojoPipelineToModelInfoConverter modelInfoConverter,
      ScoreRequestTransformer scoreRequestTransformer,
      CsvToMojoFrameConverter csvConverter) {
    return new MojoScorer(
            requestConverter,
            responseConverter,
            contributionRequestConverter,
            contributionResponseConverter,
            modelInfoConverter,
            scoreRequestTransformer,
            csvConverter);
  }
}
