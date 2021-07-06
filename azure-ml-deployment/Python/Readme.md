# Deploy H2O DAI Mojo models to Azure ML Services using Python

This template is designed to show how to deploy Driverless AI Mojo models to Azure Machine Learning Services using python. 


### Requisites 

- Driverless AI License 
- Driverless AI Mojo Model
- Driverless AI C++ Runtime Mojo and Datatable package - http://docs.h2o.ai/driverless-ai/latest-stable/docs/userguide/scoring-pipeline-cpp.html
- An Azure Active Directory Application and Service Principal - https://docs.microsoft.com/en-us/azure/active-directory/develop/howto-create-service-principal-portal


### Create Python Enviroment 

Make a python virtual enviroment and install the requirements 
```
conda create -n env python=3.6
activate env
pip install -r requirements.txt
```

### Configure Deployment 

To configure the deployment modify the following files: 

- config.txt
    - worspace_name: Name of the Azure Machine Learning Workspace where to deploy the model
    - resource_group_name: Name of the resource group
    - location: Azure Region where to deploy all the resources. 
    - model_name: Name that will be used to register the model on Azure
    - description: Short summary about the model
    - model_path: path to the pipeline.mojo file
    - deployment_type: Type of the Deployment (ACI, AKS, Local)
    - aks_cluster: Name of the AKS cluster to be created (only if AKS is selected)
    - dai_mojo_path: path to the C++ Runtime Mojo
    - datatable_path: path to the datatable package
    - client_id: Application ID
    - tenant_id: Application Directory ID
    - secret_key: Application Secret key
    - azure_subscription_id: Azure Subscription ID
- deploymentconfigACI.json 
- deploymentconfigAKS.json
- deploymentconfigLocal.json



### DAI License 

Must be located in the scoring-pipeline/env directory

### Test Deployment 

In order to test the deployment, the service keys are needed. This deployment script uses secret keys as the default authentication. To get the primary and secondary keys use the azure portal or: 

```python
from azureml.core import Workspace
from azureml.core.webservice import Webservice

ws = Workspace.get(name="myworkspace", subscription_id='<azure-subscription-id>', resource_group='myresourcegroup')
service = Webservice(name='service_name', workspace=ws)
service.get_keys()

```

The test.py script shows how the web service can be consumed. 