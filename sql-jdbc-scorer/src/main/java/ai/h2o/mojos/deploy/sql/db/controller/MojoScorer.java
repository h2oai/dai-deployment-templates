package ai.h2o.mojos.deploy.sql.db.controller;

import ai.h2o.mojos.deploy.common.jdbc.SqlScorerClient;
import ai.h2o.mojos.deploy.common.rest.jdbc.model.Model;
import ai.h2o.mojos.deploy.common.rest.jdbc.model.ModelSchema;
import ai.h2o.mojos.deploy.common.rest.jdbc.model.ScoreRequest;
import ai.h2o.mojos.deploy.common.rest.jdbc.model.ScoreResponse;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class MojoScorer {

  private final SqlScorerClient sqlScorerClient;

  public MojoScorer() {
    this.sqlScorerClient = SqlScorerClient.init();
  }

  ScoreResponse score(ScoreRequest request) {
    HashMap<String, String[]> hashMap = sqlScorerClient.scoreQuery(request);
    ScoreResponse scoreResponse = new ScoreResponse();
    scoreResponse.setId(getModelId());
    scoreResponse.setSuccess(true);
    scoreResponse.setPreviewScores(Arrays.asList(hashMap.get("previewScores")));
    scoreResponse.setPreviewColumns(Arrays.asList(hashMap.get("previewColumns")));
    return scoreResponse;
  }

  String getModelId() {
    return sqlScorerClient.getModelId();
  }

  Model getModelInfo() {
    List<String> inputFeatures = Arrays.asList(sqlScorerClient.getModelInfo());
    Model model = new Model();
    ModelSchema modelSchema = new ModelSchema();
    modelSchema.setInputFields(inputFeatures);
    model.setId(sqlScorerClient.getModelId());
    model.setSchema(modelSchema);
    return model;
  }
}
