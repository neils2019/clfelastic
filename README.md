# clfelastic - Clfmon
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0)

Clfmon is a network log monitor dashboard.  Processes web server log file in
common log file format in real time through reactive stream processor with
dashboard output through Elastic Stack.

Network server log in common log format, https://en.wikipedia.org/wiki/Common_Log_Format.  Server log is processed with reactive stream processor to transform the data in real-time for dashboard metrics.  In this proof-of-concept the network log is processed to display two metrics,

* Top ranked site page requests - based on windowed aggregated number of requests per host
* Number of requests per window interval (1 min)

Metrics are collected and displayed in real-time dashboard through an Elastic Stack implementation with Kibana.

## Design
<p float="left">
<img src="https://github.com/neils2019/clfelastic/blob/master/src/main/resources/img/fileclf_general.png" width="500" height="360"/> <img src="https://github.com/neils2019/clfelastic/blob/master/src/main/resources/img/fileclf_datapipeline.png" width="500" height="360"/>
</p>

## Dashboard

<img src="https://github.com/neils2019/clfelastic/blob/master/src/main/resources/img/dashboard_stream_processed_network_log.png" width="1150" height="360"/>

## API Documentation

./target/scala-2.13/api

## How to Build
clone repo
(set desired parameters in application.conf, src/main/resources/appication.conf)

### run Docker containers for Elastic Stack and configure Elastic and Kibana
cd src/main/resources
docker-compose up -d
sh ./elastic_mappings.sh
* open kibana in browser: http://localhost:5601, 
  * goto Management->Saved Objects
* from Management->Saved Objects, 
  * import src/main/resources/export.json
* from Management->Index Patterns,
  * set essink as default index (select star)

### compile and run real-time stream processor transforming log to metrics
* log file used in main directory, 
  * set in src/resources/application.conf, log.txt
* sbt compile
* sbt run

### view dashboard
* goto kibana in browser: http://localhost:5601, 
  * goto Dashboard, 
  * Essink_host_rank and Essink1_requests_per_minute

## real time server log dashboard:
<img src="https://github.com/neils2019/clfelastic/blob/master/src/main/resources/img/dashboard_stream_processed_network_log.png" width="1150" height="360"/>

## Changes

#### Version 1.0 (in development)
