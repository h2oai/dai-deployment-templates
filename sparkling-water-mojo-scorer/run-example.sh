#!/usr/bin/env bash

#
# Simple runner of MOJO on top of PySpark
#
# Command line example:
#  ./run-example.sh mojo-pipeline/mojo2-runtime.jar --mojo mojo-pipeline/pipeline.mojo --input mojo-pipeline/example.csv
#

if [ -z "$SPARK_HOME" ]; then
    echo "SPARK_HOME is not specified!"
    exit -1
fi

if [ -z "$SPARKLING_HOME" ]; then
    echo "SPARKLING_HOME is not specified!"
    exit -1
fi

MOJO_RUNTIME=${1:-mojo2-runtime.jar}
shift

if [ ! -f "$MOJO_RUNTIME" ]; then
    echo "MOJO runtime jar file '${MOJO_RUNTIME}' is not found!"
    exit -1
fi

if [ ! -f "$DRIVERLESS_AI_LICENSE_FILE" ]; then
    echo "MOJO license file '${DRIVERLESS_AI_LICENSE_FILE}' is not found!"
    exit -1
fi

SPARKLING_WATER_ZIP=$(find $SPARKLING_HOME/py/build/dist -name "h2o_pysparkling*.zip" -type f)
SPARK_CONF="--conf spark.driver.extraClassPath=${MOJO_RUNTIME} --conf spark.executor.extraClassPath=${MOJO_RUNTIME}"
CMD="$SPARK_HOME/bin/spark-submit --master ${MASTER:-local[*]} --driver-memory=8g --jars ${MOJO_RUNTIME},${DRIVERLESS_AI_LICENSE_FILE} --py-files $SPARKLING_WATER_ZIP ${SPARK_CONF}"

cat <<EOF
Using:
  SPARK_HOME     : ${SPARK_HOME}
  SPARKLING_HOME : ${SPARKLING_HOME}
  SPARKLING_WATER_ZIP: ${SPARKLING_WATER_ZIP}
  LAUNCHER_CMD   : ${CMD}
  APP PARAMS     : $@
  JAVA           : $(java -version)

EOF

$CMD example.py "$@"

