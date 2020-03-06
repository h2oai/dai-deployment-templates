package ai.h2o.mojos.deploy.common.jdbc

import java.io.{File, IOException}
import java.net.URL
import java.util
import java.util.Properties

import ai.h2o.mojos.deploy.common.rest.jdbc.model.ScoreRequest.SaveMethodEnum
import ai.h2o.mojos.deploy.common.rest.jdbc.model.ScoreRequest
import ai.h2o.mojos.deploy.common.jdbc.utils._
import ai.h2o.mojos.runtime.lic.LicenseException
import ai.h2o.sparkling.ml.models.H2OMOJOPipelineModel
import com.google.common.base.{Preconditions, Strings}
import org.apache.spark.SparkConf
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.slf4j.{Logger, LoggerFactory}

object SqlScorerClient {
  def init(): SqlScorerClient = {
    val sqlScorerClient = new SqlScorerClient()
    sqlScorerClient
  }
}

class SqlScorerClient {
  val logger: Logger = LoggerFactory.getLogger(classOf[SqlScorerClient])

  val conf: SparkConf = new SparkConf()
    .setAppName(System.getProperty("app.name", "h2oaiSparkSqlScorer"))
    .setMaster(System.getProperty("spark.master.node", "local[*]"))
    .set("spark.ui.enabled", "false")
  val sparkSession: SparkSession = SparkSession.builder().config(conf).getOrCreate()

  val MOJO_PIPELINE_PATH_PROPERTY: String = "mojo.path"
  val MOJO_PIPELINE_PATH: String = System.getProperty(MOJO_PIPELINE_PATH_PROPERTY)
  val pipeline: H2OMOJOPipelineModel = loadMojoPipelineFromFile()

  val JDBC_CONFIG_FILE_PROPERTY: String = "jdbc.config.path"
  val JDBC_CONFIG_FILE: String = System.getProperty(JDBC_CONFIG_FILE_PROPERTY)
  val jdbcConfig: JdbcConfig = loadJdbcConfigFromFile()

  val sqlProperties: Properties = setSqlProperties()

  def scoreQuery(scoreRequest: ScoreRequest): util.HashMap[String, Array[String]] = {
    var preds: DataFrame = null
    val df: DataFrame = read(scoreRequest)
    if (scoreRequest.getIdColumn != null) {
      preds = pipeline
        .transform(df)
        .select(sanitizeInputString(scoreRequest.getIdColumn), "prediction.*")
    } else {
      preds = pipeline.transform(df).select("prediction.*")
    }
    if (!scoreRequest.getSaveMethod.equals(SaveMethodEnum.PREVIEW)) {
      logger.info(
        "Received expected save method {}, attempting to write to database",
        scoreRequest.getSaveMethod.toString
      )
      write(scoreRequest, preds)
    }
    val hashMap: util.HashMap[String, Array[String]] = new util.HashMap[String, Array[String]]()
    hashMap.put("previewScores", castDataFrameToArray(preds, 5))
    hashMap.put("previewColumns", preds.columns)
    hashMap
  }

  def getModelInfo: Array[String] = {
    pipeline.getFeaturesCols()
  }

  def getModelId: String = {
    pipeline.uid
  }

  private def read(scoreRequest: ScoreRequest): DataFrame = {
    logger.info("Recieved request to score sql query")
    logger.info(
      String.format(
        "Query inputs: query - %s, outputTable - %s, idColumn - %s",
        scoreRequest.getSqlQuery,
        scoreRequest.getOutputTable,
        scoreRequest.getIdColumn
      )
    )
    val sqlQuery: String = String.format(
      "(%s) queryTable",
      sanitizeInputString(scoreRequest.getSqlQuery)
    )
    if (scoreRequest.getIdColumn != null) {
      logger.info("Generating partitions for query: {}", sqlQuery)
      generatePartitionMapping(
        jdbcConfig.dbConnectionString,
        sqlQuery,
        sanitizeInputString(scoreRequest.getIdColumn)
      )
    }
    logger.info("Executing query")
    doQuery(jdbcConfig.dbConnectionString, sqlQuery)
  }

  private def write(scoreRequest: ScoreRequest, predsDf: DataFrame): Unit = {
    logger.info("Writing scored data to table")
    predsDf
      .write
      .mode(castSaveModeFromRequest(scoreRequest))
      .jdbc(
        url = jdbcConfig.dbConnectionString,
        table = scoreRequest.getOutputTable,
        sqlProperties
      )
  }

  private def loadMojoPipelineFromFile(): H2OMOJOPipelineModel = {
    Preconditions.checkArgument(
      !Strings.isNullOrEmpty(MOJO_PIPELINE_PATH),
      "Path to mojo pipeline not specified, set the %s property",
      MOJO_PIPELINE_PATH_PROPERTY
    )
    logger.info("Spark: Loading Mojo pipeline from path {}", MOJO_PIPELINE_PATH)
    var mojoFile: File = new File(MOJO_PIPELINE_PATH)
    if (!mojoFile.isFile) {
      val classLoader: ClassLoader = SqlScorerClient.getClass.getClassLoader
      val resourcePath: URL = classLoader.getResource(MOJO_PIPELINE_PATH)
      if (resourcePath != null) {
        mojoFile = new File(resourcePath.getFile)
      }
    }
    if (!mojoFile.isFile) {
      throw new RuntimeException("Could not load mojo")
    }
    try {
      val pipeline: H2OMOJOPipelineModel = H2OMOJOPipelineModel.createFromMojo(mojoFile.getPath)
      logger.info("Mojo pipeline successfully loaded ({})", pipeline.uid)
      pipeline
    } catch {
      case e: IOException =>
        throw new RuntimeException("Unable to load mojo", e)
      case e: LicenseException =>
        throw new RuntimeException("License file not found", e)
    }
  }

  private def loadJdbcConfigFromFile(): JdbcConfig = {
    Preconditions.checkArgument(
      !Strings.isNullOrEmpty(JDBC_CONFIG_FILE),
      "Path to jdbc config file is not specified, set the %s property",
      JDBC_CONFIG_FILE_PROPERTY
    )
    logger.info("Loading the JDBC Config file from path {}", JDBC_CONFIG_FILE)
    try {
      new JdbcConfig(JDBC_CONFIG_FILE)
    } catch {
      case e: IOException =>
        throw new RuntimeException("Unable to load JDBC Config file", e)
    }
  }

  private def generatePartitionMapping(url: String, query: String, idColumn: String): Unit = {
    val tmpDf: DataFrame = doQuery(url, query)
    logger.info("Attempting to cast arbitrary response types to Int")
    val minimum: Int = castToInt(tmpDf.selectExpr(String.format("MIN(%s)", idColumn)).first().get(0))
    val maximum: Int = castToInt(tmpDf.selectExpr(String.format("MAX(%s)", idColumn)).first().get(0))
    val numPartitions: Int = math.ceil((maximum - minimum + 1) / 50000.0).toInt
    logger.info(String.format(
      "%s Partitions generated with: upper bound [%s], lower bound [%s], and partition column [%s]",
      numPartitions.toString,
      maximum.toString,
      minimum.toString,
      idColumn
    ))
    sqlProperties.setProperty("numPartitions", numPartitions.toString)
    sqlProperties.setProperty("upperBound", maximum.toString)
    sqlProperties.setProperty("lowerBound", minimum.toString)
    sqlProperties.setProperty("partitionColumn", idColumn)
  }

  private def doQuery(url: String, query: String): DataFrame = {
    sparkSession.read.jdbc(
      url = url,
      table = query,
      properties = sqlProperties
    )
  }

  private def setSqlProperties(): Properties = {
    val properties: Properties = new Properties()
    properties.setProperty("user", jdbcConfig.dbUser)
    properties.setProperty("password", jdbcConfig.dbPassword)
    properties.setProperty("driver", jdbcConfig.dbDriver)
    properties
  }
}
