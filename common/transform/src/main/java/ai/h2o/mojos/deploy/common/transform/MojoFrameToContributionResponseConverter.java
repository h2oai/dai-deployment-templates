package ai.h2o.mojos.deploy.common.transform;

import ai.h2o.mojos.deploy.common.rest.model.ContributionGroup;
import ai.h2o.mojos.deploy.common.rest.model.ContributionResponse;
import ai.h2o.mojos.deploy.common.rest.model.Row;
import ai.h2o.mojos.runtime.frame.MojoFrame;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MojoFrameToContributionResponseConverter {

  /**
   * Converts the resulting predicted {@link MojoFrame} into the API response object {@link
   * ContributionResponse}.
   */
  public ContributionResponse contributionResponseWithNoOutputGroup(MojoFrame shapleyMojoFrame) {
    List<Row> outputRows =
        Stream.generate(Row::new)
            .limit(shapleyMojoFrame.getNrows())
            .collect(Collectors.toList());
    Utils.copyResultFields(shapleyMojoFrame, outputRows);

    List<String> outputFeatureNames = Arrays.asList(shapleyMojoFrame.getColumnNames());

    ContributionResponse contributionResponse = new ContributionResponse();
    contributionResponse.setFeatures(outputFeatureNames);

    ContributionGroup contributionGroup = new ContributionGroup();
    contributionGroup.setContributions(outputRows);
    // for REGRESSION and BINOMIAL models the contribution response
    // contains only one ContributionGroup object
    contributionResponse.addContributionGroupsItem(contributionGroup);

    return contributionResponse;
  }

  /**
   * Converts the resulting predicted {@link MojoFrame} into the API response object {@link
   * ContributionResponse grouped by the strings called as outputgroupNames}.
   */
  public ContributionResponse contributionResponseWithOutputGroup(
      MojoFrame shapleyMojoFrame, List<String> outputGroupNames) {
    int rowCount = shapleyMojoFrame.getNrows();
    List<String> columnNames = Arrays.asList(shapleyMojoFrame.getColumnNames());

    ContributionResponse contributionResponse = new ContributionResponse();
    contributionResponse.setContributionGroups(new ArrayList<>());
    List<String> featureNames = new ArrayList<>();
    boolean isFirstOutputGroup = true;

    for (String outputGroupName : outputGroupNames) {
      ContributionGroup contributionGroup = createContributionGroup(rowCount, outputGroupName);
      Pattern pattern = Pattern.compile("\\." + outputGroupName);

      // note: columnNames from mojo contains a combination of featureName and outputGroupName
      // columnNames are expected to have pattern featureName.outputGroupName

      for (int i = 0; i < columnNames.size(); i++) {
        Matcher matcher = pattern.matcher(columnNames.get(i));
        if (matcher.find()) {
          if (isFirstOutputGroup) {
            String featureName = columnNames.get(i).substring(0, matcher.start());
            featureNames.add(featureName);
          }
          String[] columnDataFromMojo = shapleyMojoFrame.getColumn(i).getDataAsStrings();
          for (int k = 0; k < rowCount; k++) {
            List<String> existingRow = contributionGroup.getContributions().get(k);
            existingRow.add(columnDataFromMojo[k]);
          }
        }
      }
      contributionResponse.addContributionGroupsItem(contributionGroup);
      isFirstOutputGroup = false;
    }
    contributionResponse.setFeatures(featureNames);
    return contributionResponse;
  }

  private ContributionGroup createContributionGroup(int rowCount, String outputGroupName) {
    ContributionGroup contributionGroups = new ContributionGroup();
    contributionGroups.setOutputGroup(outputGroupName);
    contributionGroups.setContributions(
        Stream.generate(Row::new).limit(rowCount).collect(Collectors.toList()));
    return contributionGroups;
  }
}
