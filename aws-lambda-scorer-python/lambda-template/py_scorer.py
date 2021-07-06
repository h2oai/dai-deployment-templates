import json
import os
import threading

import boto3
import botocore
import daimojo.model
import datatable as dt


class MojoPipeline(object):
    """Mojo Pipeline instance singleton"""
    _model = None
    _instance = None
    _lock = threading.Lock()

    def __new__(cls):
        if MojoPipeline._instance is None:
            with MojoPipeline._lock:
                if MojoPipeline._instance is None:
                    MojoPipeline._instance = super(MojoPipeline, cls).__new__(cls)
                    MojoPipeline._instance.setup()
        return MojoPipeline._instance

    def setup(self):
        """Download and read mojo during instance creation"""

        bucket_name = os.environ.get('DEPLOYMENT_S3_BUCKET_NAME')
        bucket_key = os.environ.get('MOJO_S3_OBJECT_KEY')
        try:
            s3 = boto3.resource('s3')
            s3.Bucket(bucket_name).download_file(bucket_key, '/tmp/pipeline.mojo')
            self._model = daimojo.model("/tmp/pipeline.mojo")
        except botocore.exceptions.ClientError:
            raise NotFound("Not able to download the mojo file")

    def get_feature_names(self):
        """Return feature names"""

        return self._model.feature_names

    def get_types(self):
        """Get column types"""

        types = {}
        for index, value in enumerate(self._model.feature_names):
            types[value] = self._model.feature_types[index]
        return types

    def get_missing_values(self):
        """Return mojo missing values"""

        return self._model.missing_values

    def get_prediction(self, d_frame):
        """Score and return predictions on a given dataset"""

        return self._model.predict(d_frame)


class ScorerError(Exception):
    """Base Scorer Error"""
    status_code = 500


class InternalError(ScorerError):
    """Internal Error"""
    status_code = 500


class BadRequest(ScorerError):
    """Bad request"""
    status_code = 400


class NotFound(ScorerError):
    """Not found"""
    status_code = 404


def scoring_request(request_body, request_id):

    mojo = MojoPipeline()

    if all(feature in request_body.keys() for feature in mojo.get_feature_names()):
        raise BadRequest("Bad request body")

    columns_length = len(request_body['fields'])
    for row in request_body['rows']:
        if len(row) != columns_length:
            raise BadRequest("Columns length does not match rows length")

    # properly define types based on the request elements order
    tmp_frame = dt.Frame(
        [list(x) for x in list(zip(*request_body['rows']))],
        names=list(mojo.get_types().keys()),
        stypes=list(mojo.get_types().values())
    )

    # fill up the missing values and use the tmp frame to avoid overriding to a lower type
    # example from 'Str32' down to 'Float32/hex'
    d_frame = dt.fread(
        text=tmp_frame.to_csv(),
        columns=list(mojo.get_types().keys()),
        na_strings=mojo.get_missing_values()
    )

    res = mojo.get_prediction(d_frame)

    return {
        'id': request_id,
        'score': list(zip(*res.to_list()))
    }


def request_handler(event, context):
    request_id = ''
    try:
        if 'requestContext' not in event.keys() or 'requestId' not in event['requestContext']:
            raise BadRequest("Request does not seem to be relayed through an AWS API Gateway")
        else:
            request_id = event['requestContext']['requestId']

        if event['httpMethod'] != 'POST':
            raise BadRequest("Unsupported http action.")

        if event['resource'] != '/score':
            raise NotFound("Resource not found.")

        if 'body' not in event.keys():
            raise BadRequest("Invalid request. Need a request body.")

        request_body = json.loads(event['body'])

        if request_body is None or len(request_body.keys()) == 0:
            raise BadRequest("Invalid request. Need a request body.")

        if 'fields' not in request_body.keys() or not isinstance(request_body['fields'], list):
            raise BadRequest("Cannot determine the request column fields")

        if 'rows' not in request_body.keys() or not isinstance(request_body['rows'], list):
            raise BadRequest("Cannot determine the request rows")

        scoring_result = scoring_request(request_body, request_id)

        return {
            'isBase64Encoded': False,
            'statusCode': 200,
            'body': json.dumps(scoring_result)
        }
    except ScorerError as e:
        return {
            'isBase64Encoded': False,
            'statusCode': e.status_code,
            'body': json.dumps({'id': request_id, 'message': str(e)})
        }
    except Exception as exc:
        print(exc)
        return {
            'isBase64Encoded': False,
            'statusCode': 500,
            'body': json.dumps({'id': request_id, 'message': str(exc)})
        }
