# DAI Deployment Template for AWS Lambda

This package contains:
* Sources of the generic lambda implementation in: `lambda-template`
* Parameterized terraform files for pushing the lambda to AWS in:
  `terraform-recipe`


## Building the `lambda-template`

The code of the AWS Lambda scorer is a gradle project build as usual by
`./gradlew build`. The build result is a Zip archive
`lambda-template/build/distributions/lambda-template.zip` containing a general
Mojo scorer that can be directly pushed to AWS.

The Scorer relies on the following environment variables:
* `DEPLOYMENT_S3_BUCKET_NAME`: Name of the AWS S3 bucket storing the Mojo file.
* `MOJO_S3_OBJECT_KEY`: Key of Mojo file AWS S3 object.
* `DRIVERLESS_AI_LICENSE_KEY`: The Driverless license key.


## Pushing to AWS Using Terraform

This deployment template is meant to be used by Driverless AI backend directly,
not by hand. The following describes step necessary to push the lambda by hand,
e.g., for testing purposes.

### One-off Setup

Install terraform following steps in: https://www.terraform.io/downloads.html.

Initialize terraform by running `terraform init` in the `terraform-recipe`
folder.
This will download all necessary Terraform plugins, e.g., the AWS one.

### Pushing Lambda to AWS

The Terraform recipe in `terraform-recipe` relies on a few variables you need
to provide either by hand, from command line, or by setting corresponding
environmental variables (using the prefix `TF_VAR_`):
* `access_key`: The access key to your AWS account.
* `secret_key`: The secret key to your AWS account.
* `region`: AWS region to push to (optional, defaults to `us-east-1`).
* `lambda_id`: Id of the resulting AWS lambda. Keep that unique as it is also
  used to store other fragments, e.g., the Mojo file in S3.
* `lambda_zip_path`: Local path to the actual lambda scorer distribution, see
  above (optional, defaults to the relative path to the build Zip archive
  above).
* `mojo_path`: Local path to the mojo file to be pushed to S3. You may get one,
  e.g., by running Driverless AI on `test/data/iris.csv` and asking it to
  create and download the Mojo scoring pipeline in the UI.
* `license_key`: Driverless AI license key.

Once all the non-optional variables are set, the following command will push
the lambda (or update any changes thereof): `terraform apply`.

Upon successful push, Terraform will output the URL of the lambda endpoint and
the corresponding `api_key`.
Note that the recipe sets up AWS API Gateway proxy, see `api_gateway.tf`.
Look for `base_url` and `api_key` in the output.

```text
Outputs:

api_key = DXQtiCbqEY6xjXWP1MMCu4nkDTwRgfdX2qZoKm3e
base_url = https://mslmi91tni.execute-api.us-east-1.amazonaws.com/test
```

To test the endpoint, send a request to this URL appended by `score` and include
the `api_key` in the request header `x-api-key`, e.g., as follows.

```bash
$ curl \
    -X POST \
    -H "x-api-key: DXQtiCbqEY6xjXWP1MMCu4nkDTwRgfdX2qZoKm3e" \
    -d @test.json https://mslmi91tni.execute-api.us-east-1.amazonaws.com/test/score
```

This expects a file `test.json` with the actual scoring request payload.
If you are using the mojo trained in `test/data/iris.csv` as suggested above,
you should be able to use the following json payload:

```json
{
  "fields": [
    "sepal_len", "sepal_wid", "petal_len", "petal_wid"
  ],
  "includeFieldsInOutput": [
    "sepal_len"
  ],
  "rows": [
    [
      "1.0", "1.0", "2.2", "3.5"
    ],
    [
      "3.0", "10.0", "2.2", "3.5"
    ],
    [
      "4.0", "100.0", "2.2", "3.5"
    ]
  ]
}
```

The expected response should follow this structure, but the actual values may differ:

```json
{
  "score": [
    [
      "1.0",
      "0.6240277982943945",
      "0.045458571508101536",
      "0.330513630197504"
    ],
    [
      "3.0",
      "0.7209441819603676",
      "0.06299909138586585",
      "0.21605672665376663"
    ],
    [
      "4.0",
      "0.7209441819603676",
      "0.06299909138586585",
      "0.21605672665376663"
    ]
  ]
}
```
