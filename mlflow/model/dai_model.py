import os
import shutil
from sys import version_info

import argparse
import cloudpickle
import mlflow.pyfunc


def get_env():
    python_version = "{major}.{minor}.{micro}".format(
        major=version_info.major,
        minor=version_info.minor,
        micro=version_info.micro,
    )
    daimojo_path = (
        "https://s3.amazonaws.com/artifacts.h2o.ai/releases/"
        "ai/h2o/daimojo/0.19.6%2Bmaster.460/x86_64-centos7/"
        "daimojo-0.19.6%2Bmaster.460-cp36-cp36m-linux_x86_64.whl"
    )

    conda_env = {
        "channels": ["defaults"],
        "dependencies": [
            "python={}".format(python_version),
            "pip",
            {
                "pip": [
                    "mlflow",
                    "datatable",
                    "--no-binary=protobuf protobuf",
                    daimojo_path,
                    "cloudpickle=={}".format(cloudpickle.__version__),
                ],
            },
        ],
        "name": "dai_env",
    }
    return conda_env


def get_artifacts(mojo_path, license_path):
    artifacts = {
        "dai_model": mojo_path,
        "license_key": license_path,
    }
    return artifacts


class DAIWrappedModel(mlflow.pyfunc.PythonModel):
    def load_context(self, context):
        import daimojo.model
        import os

        self.model = daimojo.model(context.artifacts["dai_model"])
        license_file = context.artifacts["license_key"]
        os.environ["DRIVERLESS_AI_LICENSE_FIlE"] = license_file

    def predict(self, context, x):
        import datatable as dt

        pred = self.model.predict(dt.Frame(x))
        if pred.shape[1] == 2:
            pred = pred[:, 1]
        return pred.to_pandas()


def save_model(model_name, mojo_path, license_path):
    conda_env = get_env()
    artifacts = get_artifacts(mojo_path, license_path)

    mlflow.pyfunc.save_model(
        path=model_name,
        python_model=DAIWrappedModel(),
        artifacts=artifacts,
        conda_env=conda_env,
    )


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description=(
            "Save the Driverless AI model as MLFlow model"
            "arguments: --license license.sig --mojo pipeline.mojo --output "
            "dai_model"
        )
    )
    parser.add_argument(
        "--output", type=str, help="Name of the MLFlow model",
    )
    parser.add_argument(
        "--license",
        type=str,
        help="path to license, defaults to 'license.sig'",
        default="license.sig",
    )
    parser.add_argument(
        "--mojo",
        type=str,
        help="path to license, defaults to 'pipeline.mojo'",
        default="pipeline.mojo",
    )
    args = parser.parse_args()
    save_model(args.output, args.mojo, args.license)
    # Try to load saved model
    loaded_model = mlflow.pyfunc.load_model(args.output)
