# DAI Deployment Template for Sagemaker Hosted C++ Scorer

## Overview

### Build Image

Run the following command to build the docker image.

```bash
docker build -t <aws_account_id>.dkr.ecr.<region>.amazonaws.com/h2oai/sagemaker-hosted-scorer:<tag> .
```

Verify that the Docker image was created, and take note of the version created.

```bash
docker images --format "{{.Repository}} \t {{.Tag}}" | grep "h2oai/sagemaker-hosted-scorer"
```

### Optional: Test the build

After building, run to test the produced Docker container locally like this:

Step 1:  Put a pipeline.mojo and valid license.sig into this directory (aws-sagemaker-hosted-scorer-cpp).

Step 2:  Start the docker instance.


```
docker run \
    --rm \
    --init \
    -ti \
    -v `pwd`:/opt/ml/model \
    -p 8080:8080 \
    harbor.h2o.ai/opsh2oai/h2oai/sagemaker-hosted-scorer:<tag>
```
Step 3:  Use the following curl command to test the container locally:

```
curl \
    -X POST \
    -H "Content-Type: application/json" \
    -d @payload.json http://localhost:8080/invocations
```

payload.json:

```
{
  "fields": [
    "field1", "field2"
  ],
  "includeFieldsInOutput": [
    "field2"
  ],
  "rows": [
    [
      "value1", "value2"
    ],
    [
      "value1", "value2"
    ]
  ]
}
```


### Deploy to SageMaker

Create `h2oai/sagemaker-hosted-scorer` repository in Sagemaker for the scorer service image.

Use the output of the command below to `aws ecr login`:

```
aws ecr get-login-password --region <region> | docker login --username AWS --password-stdin <aws_account_id>.dkr.ecr.<region>.amazonaws.com
```

Then push the scorer service image to AWS ECR (Elastic Container Registry):

```
docker push <aws_account_id>.dkr.ecr.<region>.amazonaws.com/h2oai/sagemaker-hosted-scorer:<tag>
```

Then create a model package with the pipeline file and the license key, and copy it to S3:

```
tar cvf mojo.tar pipeline.mojo license.sig
gzip mojo.tar
aws s3 cp mojo.tar.gz s3://<your-bucket>/
```

Next create the appropriate model and endpoint on Sagemaker.
Check that the endpoint is available with `aws sagemaker list-endpoints`.
