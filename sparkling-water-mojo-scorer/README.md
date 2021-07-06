# Example of using MOJO2 from PySparkling
Multi-threaded MOJO scorer using Sparkling Water.

## Requirements

  - Java 8
  - Configure `SPARK_HOME` to point to Spark distribution directory
  - Configure `SPARKLING_HOME` to point to to Sparkling Water distribution directory
  - Configure `DRIVERLESS_AI_LICENSE_FILE` to point to an active DAI license.sig file

## (optional) 

  - Configure `MASTER` to point to the master url for the cluster
  - Create and use a Conda environment

    ```
    conda create -n mojo python=3.6
    conda activate mojo
    conda install openjdk
    conda install numpy
    conda install pandas
    pip install h2o_pysparkling_2.4
    ```

## Usage

Run `./run-example.sh mojo2-runtime.jar --mojo mp1/pipeline.mojo --input example.csv`

## FAQ
  - I have problems with showing Spark DataFrames due to ascii encoding error in Python session.
    Please export, `export PYTHONIOENCODING=utf-8`

