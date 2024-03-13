import os
import json
from azureml.core.model import Model
import pandas as pd

def init():
    
    with open('./scoring-pipeline/env/license.sig', 'r') as f:
        dai_license = f.read().replace('\n','') 
    
    os.environ["DRIVERLESS_AI_LICENSE_KEY"] = dai_license    
    
    import daimojo.model

    global model
    model = daimojo.model("./scoring-pipeline/pipeline.mojo")
    

def run(raw_data):
    try:
        import datatable as dt
        data = json.loads(raw_data)['data']
        data = pd.read_json(data, orient='records')        
        pydt = dt.Frame(data)
        
        res = model.predict(pydt)
        
        return res.to_list()

    except Exception as e:
        error = str(e)
        return error