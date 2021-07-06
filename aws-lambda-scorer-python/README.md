# DAI Deployment Template for AWS Lambda Python Scoring Pipeline

Request example:
----------------
```bash
curl -X POST -d @test.json -H "x-api-key: XXXXXXXXXXX" https://xxxxxxxx.execute-api.ca-central-1.amazonaws.com/test/score
```

```json
{
  "fields": [
    "LIMIT_BAL",
    "SEX",
    "EDUCATION",
    "MARRIAGE",
    "AGE",
    "PAY_0",
    "PAY_2",
    "PAY_3",
    "PAY_4",
    "PAY_5",
    "PAY_6",
    "BILL_AMT1",
    "BILL_AMT2",
    "BILL_AMT3",
    "BILL_AMT4",
    "BILL_AMT5",
    "BILL_AMT6",
    "PAY_AMT1",
    "PAY_AMT2",
    "PAY_AMT3",
    "PAY_AMT4",
    "PAY_AMT5",
    "PAY_AMT6"
  ],
  "rows": [
    [
      "60000.0",
      "0.0",
      "1.0",
      "0.0",
      "25.0",
      "-2.0",
      "5.0",
      "2.0",
      "4.0",
      "0.0",
      "0.0",
      "-14386.0",
      "-7334.0",
      "-2640.0",
      "-3684.0",
      "-243.0",
      "-1560.0",
      "0.0",
      "41.0",
      "5.0",
      "2.0",
      "30.0",
      "100.0"
    ],
    [
      "60000.0",
      "0.0",
      "1.0",
      "0.0",
      "25.0",
      "-2.0",
      "5.0",
      "2.0",
      "4.0",
      "0.0",
      "0.0",
      "-14386.0",
      "-7334.0",
      "-2640.0",
      "-3684.0",
      "-243.0",
      "-1560.0",
      "0.0",
      "41.0",
      "5.0",
      "2.0",
      "30.0",
      "100.0"
    ]
  ]
}

```

Response Example:
-----------------

```json
{
  "id": "b917dd72-aa9b-4013-aad9-f593ae2a72a2",
  "score": [
    [
      0.48030006885528564,
      0.5196999311447144
    ],
    [
      0.48030006885528564,
      0.5196999311447144
    ]
  ]
}
```