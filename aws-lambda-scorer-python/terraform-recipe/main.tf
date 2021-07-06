variable "access_key" {
  description = "Access key to an AWS account to use."
}
variable "secret_key" {
  description = "Secret key to an AWS account to use."
}
variable "region" {
  description = "AWS region to push into."
  default = "us-east-1"
}
variable "lambda_id" {
  description = "Id of the resulting AWS lambda. Keep unique, it is used to store other fragments, e.g., mojo in S3."
}
variable "lambda_zip_path" {
  description = "Local path to the actual lambda scorer distribution."
  default = "../lambda-template/lambda_function.zip"
}
variable "license_key" {
  description = "Driverless AI license key."
}
variable "mojo_path" {
  description = "Local path to the mojo file to be pushed to S3."
}
variable "bucket_name" {
  description = "Name of S3 bucket."
}
locals {
  escaped_id = "${replace("h2oai_${var.lambda_id}", "/[^-_a-zA-Z0-9]/", "")}"
  bucket_arn = "arn:aws:s3:::${var.bucket_name}"
}

provider "aws" {
  access_key = "${var.access_key}"
  secret_key = "${var.secret_key}"
  region = "${var.region}"
}

resource "aws_s3_bucket_object" "mojo" {
  bucket = "${var.bucket_name}"
  key = "${var.lambda_id}.mojo"
  source = "${var.mojo_path}"
  etag = "${md5(file(var.mojo_path))}"
}

// AWS Lambda function with a Java implementation of the Mojo scorer.
resource "aws_lambda_function" "scorer" {
  function_name = "${local.escaped_id}"
  description = "H2O Driverless AI Mojo Scorer (${var.lambda_id})"
  filename = "${var.lambda_zip_path}"
  handler = "py_scorer.request_handler"
  source_code_hash = "${base64sha256(file(var.lambda_zip_path))}"
  role = "${aws_iam_role.scorer_iam_role.arn}"
  runtime = "python3.6"

  // Increase resource constraints from the defaults of 3s and 128MB.
  timeout = 900
  memory_size = 3008

  environment {
    variables = {
      DEPLOYMENT_S3_BUCKET_NAME = "${var.bucket_name}"
      MOJO_S3_OBJECT_KEY = "${aws_s3_bucket_object.mojo.key}"
      DRIVERLESS_AI_LICENSE_KEY = "${var.license_key}"
      // This is not used yet by the scorer, but it is here to enforce code reload when only the mojo itself changes.
      // Otherwise, Terraform wouldn't push a lambda change and any instance still live in AWS would keep using the
      // mojo already loaded in memory.
      MOJO_FINGERPRINT = "${aws_s3_bucket_object.mojo.etag}"
    }
  }
}

# IAM role which dictates what other AWS services the lambda function may access.
resource "aws_iam_role" "scorer_iam_role" {
  name = "${local.escaped_id}"

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
  name = "${local.escaped_id}"
  description = "Allow H2O Driverless AI Mojo Scorer to access the associated model file on S3"

  policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:ListBucket"],
      "Resource": ["${local.bucket_arn}"]
    },
    {
      "Effect": "Allow",
      "Action": ["s3:GetObject"],
      "Resource": ["${local.bucket_arn}/${aws_s3_bucket_object.mojo.key}"]
    }
  ]
}
EOF
}

resource "aws_iam_role_policy_attachment" "s3_attach" {
  role = "${aws_iam_role.scorer_iam_role.name}"
  policy_arn = "${aws_iam_policy.s3_policy.arn}"
}
