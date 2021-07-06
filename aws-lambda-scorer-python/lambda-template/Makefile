
.PHONY: help clean package

# Location of mojo wheel
MOJO_WHL := daimojo-*.whl

help:
	@echo
	@echo "  help           display this help"
	@echo "  clean          clean existing artifacts"
	@echo "  package        create a lambda deployment package named lambda_function.zip"
	@echo

clean:
	rm -rf aws_lambda_env
	rm -rf pip_package

package: clean
	rm -f lambda_function.zip
	virtualenv -p python3.6 --system-site-packages aws_lambda_env
	aws_lambda_env/bin/python -m pip install --target ./pip_package $(MOJO_WHL)
	aws_lambda_env/bin/python -m pip install --target ./pip_package -r requirements.txt
	cd pip_package && rm -r daimojo/tf_* && zip -r9 -q ../lambda_function.zip .
	zip -g lambda_function.zip py_scorer.py
	make clean
