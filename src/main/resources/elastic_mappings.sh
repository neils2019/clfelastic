#!/bin/bash

curl -X PUT "localhost:9200/essink?pretty" -H 'Content-Type: application/json' -d'
{
  "mappings": {
   "_doc": {
    "properties": {
      "time":    { "type": "date" },  
      "host":  { "type": "keyword"  },
      "numReq": { "type": "integer" }
    }
  }
 }
}
'
curl -X PUT "localhost:9200/essink1?pretty" -H 'Content-Type: application/json' -d'
{
  "mappings": {
   "kafkaconnect": {
    "properties": {
      "time":    { "type": "date" },  
      "numReq":  { "type": "integer"  },
      "alrm": { "type": "keyword" }
    }
  }
 }
}
'
