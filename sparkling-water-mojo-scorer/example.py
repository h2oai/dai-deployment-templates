import argparse

from pyspark.sql import SparkSession
from pyspark.sql.functions import monotonically_increasing_id


def _parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--mojo", type=str, required=True, 
            help="Location of .mojo file, e.g., 'file:///tmp/pipeline.mojo'")
    parser.add_argument("--input", type=str, required=True, 
            help="Location of input file, e.g., 'file:///tmp/example.csv'")
    parser.add_argument("--output", type=str, required=False, 
            help="Directory location of output files, if not specified output is printed to console")
    parser.add_argument("--format", type=str, required=False, default='csv', 
            help="Format of input/output file, if not specified format is csv")
    parser.add_argument("--format_options", type=dict, required=False, default={'header': True}, 
            help="Input options for the underlying data source")
    parser.add_argument("--nthreads", type=int, required=False, default=1, 
            help="Number of threads to use, defaults to 1")

    return parser.parse_args()


def _load_mojo(mojo_file):
    from pysparkling.ml import H2OMOJOPipelineModel
    mojo = H2OMOJOPipelineModel.create_from_mojo(mojo_file)
    mojo.set_named_mojo_output_columns(True)

    return mojo


def _load_data(args):
    spark = SparkSession.builder.appName('MOJO Pipeline Transformer').getOrCreate()
    spark.sparkContext.setLogLevel('WARN')

    return (spark.read.
            format(args.format).
            options(**args.format_options).
            load(args.input).
            repartition(args.nthreads).
            withColumn('', monotonically_increasing_id()))


def _make_predictions(mojo, df):
    return mojo.transform(df).select(['','prediction.*'])


def _save_predictions(df, path, file_format):
    print("Saving predictions...")
    df.write.format(file_format).mode('overwrite').option("header", "true").save(path)
    print("Predictions written to: {}".format(path))


def main():
    args = _parse_args()
    mojo = _load_mojo(args.mojo)
    df = _load_data(args)
    preds = _make_predictions(mojo, df)

    print("=============== In/Out ==============")
    print(f"Inputs : {mojo.get_input_names()}")
    print(f"Inputs : {mojo.get_output_names()}")
    print("=====================================")

    if args.output:
        _save_predictions(preds, args.output, args.format)
    else:
        preds.show(10, False)


if __name__ == '__main__':
    main()
