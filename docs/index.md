
# Deploying Driverless AI Models to Production

This documentation lists some of the scenarios to deploy **[Driverless AI](http://docs.h2o.ai/driverless-ai/latest-stable/docs/userguide/index.html)** models to production and provides guidelines to create deployment templates for the same.

The final model from a Driverless AI experiment can be exported as either a  **[MOJO scoring pipeline](http://docs.h2o.ai/driverless-ai/latest-stable/docs/userguide/scoring-mojo-pipelines.html)** or a **[Python scoring pipeline](http://docs.h2o.ai/driverless-ai/latest-stable/docs/userguide/scoring-standalone-python.html)**. The Mojo scoring pipeline comes with a **pipline.mojo** file with a Java or C++ runtime. This can be deployed in **any** environment that supports Java or C++. The Python scoring pipeline comes with the scoring **whl** file for deployment purposes. 

Below, is a list of the deployment scenarios for Driverless AI pipelines for Real-time, Batch, or Stream scoring.

```mermaid
graph LR;
  
  A["Realtime Scoring (single or multi-rows)"]-->AA[DAI MOJO Pipeline with Java Runtime];
  A-->AB[DAI MOJO Pipeline with C++ Runtime];
  A-->AC[DAI PYTHON Scoring Pipeline];
  
  AA-->AAA["As REST Server"]
  AA-->AAB["As AWS Lambda"]
  AA-->AAC["As GCP Cloud Run"]
  AA-->AAD["As AzureML"]
  AA-->AAE["As library"]
  AA-->AAF["As Nifi "]
  
  AB-->ABA["As library"]
  AB-->ABB["As Nifi "]
  
  AC-->ACA["As REST Server"]
  AC-->ACB["As Nifi "]
  
  classDef className fill:#f9f,stroke:#333,stroke-width:4px;
  class A,AA,AB className;
```
```mermaid
graph LR;
  
  A["Batch Scoring"]-->AA[DAI MOJO Pipeline with Java Runtime];
  A-->AB[DAI MOJO Pipeline with C++ Runtime];
  A-->AC[DAI PYTHON Scoring Pipeline];
  
  AA-->AAA["As Spark batch"]
  AA-->AAB["As library"]
  AA-->AAC["As Hive UDF"]
  AA-->AAD["As DB scorer"]
  AA-->AAE["As Nifi"]
  
  AB-->ABA["As library"]
  AB-->ABB["As Nifi "]
  
  AC-->ACA["As library"]
  AC-->ACB["As Nifi "]
  
  classDef className fill:#f9f,stroke:#333,stroke-width:4px;
  style A fill:#FFFFCC
  class start,A,AA,AB className;
```

```mermaid
graph LR;
  
  A["Stream Scoring"]-->AA[DAI MOJO Pipeline with Java Runtime];
  
  AA-->AAA["As Spark Stream"]
  AA-->AAB["As Task Queue "]
  AA-->AAC["As Active MQ"]
  AA-->AAD["As Kafka Topic"]
  
  classDef className fill:#f9f,stroke:#333,stroke-width:4px;
  style A fill:#FFFFCC
  class start,A,AA,AB className;
```
