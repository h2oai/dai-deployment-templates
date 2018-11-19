variable "access_key" {}
variable "secret_key" {}
variable "region" {
  default = "us-east-1"
}
variable "lambda_id" {
  default = "h2oai_dai_lambda"
}
variable "lambda_zip_path" {
  default = "../lambda-template/build/distributions/lambda-template.zip"
}
variable "license_key" {}
variable "mojo_zip_path" {}

provider "aws" {
  access_key = "${var.access_key}"
  secret_key = "${var.secret_key}"
  region = "${var.region}"
}

// Mojo file in S3.
resource "aws_s3_bucket" "deployment" {
  // Note that all the deployments share the same bucket. The default limit on the number of S3 buckets is 100, so we
  // don't want to exhaust that.
  bucket = "h2oai-dai-lambda-models"
  acl = "private"
}

resource "aws_s3_bucket_object" "mojo" {
  bucket = "${aws_s3_bucket.deployment.id}"
  key = "mojo_${var.lambda_id}.zip"
  source = "${var.mojo_zip_path}"
  etag = "${md5(file(var.mojo_zip_path))}"
}

// AWS Lambda function with a Java implementation of the Mojo scorer.
resource "aws_lambda_function" "scorer_lambda" {
  function_name = "${var.lambda_id}_function"
  description = "H2O Driverless AI Mojo Scorer"
  filename = "${var.lambda_zip_path}"
  handler = "ai.h2o.dia.deploy.aws.lambda.MojoScorer::score"
  source_code_hash = "${base64sha256(file(var.lambda_zip_path))}"
  role = "${aws_iam_role.scorer_lambda_iam_role.arn}"
  runtime = "java8"

  // Increase resource constraints from the defaults of 3s and 128MB.
  timeout = 120
  memory_size = 1024

  environment {
    variables = {
      DEPLOYMENT_S3_BUCKET_NAME = "${aws_s3_bucket.deployment.id}"
      MOJO_S3_OBJECT_KEY = "${aws_s3_bucket_object.mojo.key}"
      DRIVERLESS_AI_LICENSE_KEY = "${var.license_key}"
    }
  }
}

# IAM role which dictates what other AWS services the lambda function may access.
resource "aws_iam_role" "scorer_lambda_iam_role" {
  name = "${var.lambda_id}_iam_role"

  assume_role_policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": "sts:AssumeRole",
      "Principal": {
        "Service": "lambda.amazonaws.com"
      },
      "Effect": "Allow",
      "Sid": ""
    }
  ]
}
EOF
}

// Allow the lambda function to read the mojo file from S3.
resource "aws_iam_policy" "s3_policy" {
  name = "${var.lambda_id}_s3_policy"
  description = "Allow H2O Driverless AI Mojo Scorer to access the associated model file on S3"

  policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:ListBucket"],
      "Resource": ["${aws_s3_bucket.deployment.arn}"]
    },
    {
      "Effect": "Allow",
      "Action": ["s3:GetObject"],
      "Resource": ["${aws_s3_bucket.deployment.arn}/${aws_s3_bucket_object.mojo.key}"]
    }
  ]
}
EOF
}

resource "aws_iam_role_policy_attachment" "s3_attach" {
  role = "${aws_iam_role.scorer_lambda_iam_role.name}"
  policy_arn = "${aws_iam_policy.s3_policy.arn}"
}