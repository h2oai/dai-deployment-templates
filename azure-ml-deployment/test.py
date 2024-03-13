import requests
import json
import pandas as pd 

service_uri = "<scoring_uri>"
service_key = "<service_key>"

headers = {'Content-Type': 'application/json'}


headers['Authorization'] = 'Bearer '+service_key

print(headers)


test_file = pd.read_csv("./mojo/example.csv",index_col=None)

test_sample = json.dumps({"data": test_file.to_json(orient='records')})


response = requests.post(
    service_uri, data=test_sample, headers=headers)
print(response.status_code)
print(response.elapsed)
print(response.json())