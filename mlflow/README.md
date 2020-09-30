# DAI Deployment Template for MLFlow

This package contains implementation of MLFlow Model for Driverless AI MOJO. It has only been tested on linux x86_64, 
it should potentially work on ppc64 but would require some change (`model/dai_model.py` `daimojo` s3 link). 

## Creating MLFlow Model

Run the `model/dai_model.py` CLI tool and provide path to valid Driverless AI License file, Driverless AI MOJO and desired name of the MLFlow model.

```
python model/dai_model.py --license example/license.sig  --mojo example/pipeline.mojo --output dai_mlflow_pyfunc
```
Note: You will need to save a valid license key in the `example/license.sig` file.

## Running MLfLow Model as Local REST Server

To run the model as local REST server, use `mlflow models serve -m {model_name}`:

```bash
mlflow models serve -m dai_mlflow_pyfunc
``` 

### Score JSON Request

To test the endpoint, send a request to http://localhost:5000 as follows:


```bash
curl http://127.0.0.1:5000/invocations \
-H 'Content-Type: application/json; format=pandas-records' \
-d @example/example.json
```

The expected response should follow this structure, but the actual values may differ:

```json
[{"Default.1": 0.2936252951622009}, {"Default.1": 0.660947859287262}]
```

