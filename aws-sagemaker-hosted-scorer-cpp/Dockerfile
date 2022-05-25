FROM python:3.8

RUN apt-get update && \
    apt-get install -y libopenblas-dev && \
    rm -rf /var/lib/apt/lists/*

COPY requirements.txt /tmp/requirements.txt

RUN pip install pip==21.1 && pip install -r /tmp/requirements.txt

ENV DRIVERLESS_AI_LICENSE_FILE='/opt/ml/model/license.sig' \
    MOJO_FILE_PATH='/opt/ml/model/pipeline.mojo'

RUN mkdir -p /opt/ml/code

COPY py/scorer/mojo_cpp_scorer.py /opt/ml/code

WORKDIR /opt/ml/code

ENTRYPOINT ["gunicorn", "-w", "1", "-b", "0.0.0.0:8080", "mojo_cpp_scorer:app"]