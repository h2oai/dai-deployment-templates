#!/usr/bin/env python
# coding: utf-8

"""
Python Deployment Script for Driverless AI Mojo Models

"""

import os
import shutil 
import json
from random import randint
#Azure ML
from azureml.core import Workspace
from azureml.core.model import Model
from azureml.core.model import InferenceConfig
from azureml.core.webservice import AciWebservice, AksWebservice, Webservice
from azureml.core.compute import AksCompute, ComputeTarget
from azureml.core.webservice import LocalWebservice
#Azure Blob
from azure.storage.blob import BlockBlobService, PublicAccess
#Azure Mgmt
from azure.mgmt.resource import ResourceManagementClient
from azure.common.credentials import ServicePrincipalCredentials
from azure.mgmt.storage import StorageManagementClient
from azure.mgmt.storage.models import (
    StorageAccountCreateParameters,
    StorageAccountUpdateParameters,
    Sku,
    SkuName,
    Kind
)

# Load config
config = {}
with open('./config.txt', 'r') as f:
        args = f.read().split("\n")         
        for arg in args:  
            if arg:
                val = arg.split('=',1) 
                config[val[0]] = val[1].replace('"','')
                print(val)
            
config['storage_name'] = "h2ostore"+str(randint(100,999))

# Azure Login 

credentials = ServicePrincipalCredentials(
    client_id=config['client_id'],
    secret=config['secret_key'],
    tenant=config['tenant_id']
)
resource_client = ResourceManagementClient(credentials, config['azure_subscription_id'])
storage_client = StorageManagementClient(credentials, config['azure_subscription_id'])

resource_group_params = { 'location':config['location'] }

# Create Resource Group and Storage Account

resource_group_result = resource_client.resource_groups.create_or_update(
        config['resource_group_name'], 
        resource_group_params
    )

storage_async_operation = storage_client.storage_accounts.create(
    config['resource_group_name'],
    config['storage_name'],
    StorageAccountCreateParameters(
        sku=Sku(name=SkuName.standard_ragrs),
        kind=Kind.storage,
        location=config['location']
    )
)

storage_account = storage_async_operation.result()
storage_keys = storage_client.storage_accounts.list_keys(config['resource_group_name'], config['storage_name'])
storage_keys = {v.key_name: v.value for v in storage_keys.keys}

block_blob_service = BlockBlobService(
    account_name=config['storage_name'], account_key=storage_keys['key1'])

container_name = 'mojodependencies'
block_blob_service.create_container(container_name)

# Set the permission so the blobs are public.
block_blob_service.set_container_acl(
    container_name, public_access=PublicAccess.Container)

# Upload packages and modify dai_deploy.yml    


block_blob_service.create_blob_from_path(
    container_name, os.path.basename(config['daimojo_path']), config['daimojo_path'])
daimojo_url=block_blob_service.make_blob_url(container_name,os.path.basename(config['daimojo_path']))

shutil.copy('../scoring-pipeline/dai_deploy_template.yml', '../scoring-pipeline/dai_deploy.yml')

with open('../scoring-pipeline/dai_deploy.yml', 'r') as f:
    template = f.read()

template = template.replace('@@DAI MOJO URL@@', daimojo_url)

with open('../scoring-pipeline/dai_deploy.yml', 'w') as f:
    f.write(template)

# Create ML workspace 

ws = Workspace.create(name=config['workspace_name'].replace('"',''),
                      subscription_id=config['azure_subscription_id'],
                      resource_group=config['resource_group_name'],                      
                      location=config['location']
                     )

# Register the Model 

model = Model.register(model_path=config['model_path'],
                       model_name=config['model_name'],
                       description=config['description'],
                       workspace=ws)


# Deploy Model using Inference and Deployment defined in json file

inference_config = InferenceConfig(runtime="python",
                                   entry_script="score.py",
                                   source_directory="../scoring-pipeline/",
                                   conda_file="dai_deploy.yml")


def deploy_to_aci():
    
    with open('deploymentconfigACI.json') as json_file:
        deploy_config = json.load(json_file)
        
    deployment_config = AciWebservice.deploy_configuration(cpu_cores = deploy_config['containerResourceRequirements']['cpu'],
                                                           memory_gb = deploy_config['containerResourceRequirements']['memoryInGB'],
                                                           auth_enabled=True)
    service = Model.deploy(ws, config['service_name'], [model], inference_config, deployment_config)
    service.wait_for_deployment(show_output = True)
    
    print('web service hosted in ACI:', service.scoring_uri)
    
    
def deploy_to_aks():
    
    with open('deploymentconfigAKS.json') as json_file:
        deploy_config = json.load(json_file)
        
    prov_config = AksCompute.provisioning_configuration()

    aks_name = config['aks_cluster']
    # Create the cluster
    aks_target = ComputeTarget.create(workspace = ws,
                                        name = aks_name,
                                        provisioning_configuration = prov_config)
    
    aks_target.wait_for_completion(show_output = True)
    
    deployment_config = AksWebservice.deploy_configuration(cpu_cores = deploy_config['containerResourceRequirements']['cpu'],
                                                           memory_gb = deploy_config['containerResourceRequirements']['memoryInGB'])
    service = Model.deploy(ws, config['service_name'], [model], inference_config, deployment_config, aks_target)
    service.wait_for_deployment(show_output = True)
    print(service.state)
    
    
def deploy_to_local():
    
    with open('deploymentconfigLocal.json') as json_file:
        deploy_config = json.load(json_file)

    deployment_config = LocalWebservice.deploy_configuration(port=deploy_config['port'])
    service = Model.deploy(ws, config['service_name'], [model], inference_config, deployment_config)
    service.wait_for_deployment(show_output = True)
    print(service.state)
    

if config['deployment_type'] == 'ACI':
    deploy_to_aci()
elif config['deployment_type'] == 'AKS':
    deploy_to_aks()
else:
    deploy_to_local()
