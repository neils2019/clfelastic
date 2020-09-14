/*
Copyright 2020 Neil Joshi

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/


package org.nj.clfmon

import com.typesafe.config.ConfigFactory
import java.nio.file.{ Files, Paths }
import akka.stream._
import akka.stream.scaladsl.{ FileIO, Sink, Source }
import akka.stream.scaladsl._


import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.alpakka.elasticsearch._
import akka.stream.alpakka.elasticsearch.scaladsl._

import java.nio.file.Paths

import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient

import spray.json._
import DefaultJsonProtocol._


/**
 * A real time streaming reactive design to monitor Network traffic in Common Log File Format, CLF:
 *<p>
 * streams data from Source FileIO network log file <app.log in application.conf>;<p>
 * stream is processed through stream processor transformation functions, aggregations and output to Elasticsearch indexes;<p>
 *  Source FileIO csv file is streamed through two parallel stream processor flows to 2 separate ElasticSearch Sink indexes - <p>
 *  i.) essink - number of aggregated requests per host per <window_interval_time> minutes<p>
 *  ii.) essink1 - count of total number of requests per <window_interval_time> minutes
 *      
 * {{{
 * import org.nj.clfmon 
 *  val clf = new FileClf
 *  
 *           
 *  Source --->| Delimited '\n' |--->|  CLF network request entry |-->| Windowed| --> JUNCTION SPLITTER
 * 
 *                  |---->| Aggregate num req by host |--->| essink Sink
 * --> J SPLITTER --|
 *                  |---->| count total num req |-->| essink1 Sink
 * 
 *  In this object uses akka Flows for filters, functions, aggregation and
 *  joining / merging:
 *
 *  Filters:
 *    bs3deimited - Delimited byte stream by carridge return '\n'
 *  Functions:
 *    delimited2Clf - transform delimited stream to common log entry, CLF entry
 *    Clfw2Lwr - count number of network request, number of CLF entries, per interval
 *    Lwh2esIndex - transform data, requests per host, to elasticsearch index
 *    Lwr2esIndex - transform data, network requests, to elasticsearch index
 *  Aggregations:
 *    Clf2Clfw - windowed common log entries, CLF entries per interval
 *    Clfw2Lwh - aggregation by host, number of entries per host
  * }}}
 */

object FileClf {

  implicit val system = ActorSystem("FileClf")
  implicit val actorMaterializer = ActorMaterializer()
  
  implicit val ec = system.dispatcher

  implicit val format: JsonFormat[LogWindowedHost] = jsonFormat3(LogWindowedHost)
  implicit val format1: JsonFormat[LogWindowedReqAlrm] = jsonFormat3(LogWindowedReqAlrm)

  implicit val client = RestClient.builder(new HttpHost("localhost", 9200)).build()  

  // Get variables from 'application.conf'
  val config = ConfigFactory.load()
  val file = Paths.get(config.getString("app.log"))
  val essinkIndex = config.getString("elastic.index_windowed_req_by_host")
  val essink1Index = config.getString("elastic.index_windowed_req_n_alrm")

  val logStream = FileIO.fromPath(file)

  val stream = logStream.via(Flows.bs2delimited).via(Flows.delimited2Clf).async.via(Flows.Clf2Clfw)


  val baseWriteSettings = ElasticsearchWriteSettings()

  val reqByHost = stream.via(Flows.Clfw2Lwh).via(Flows.Lwh2esIndex).via(
    ElasticsearchFlow.create[LogWindowedHost](
      "essink",
      "_doc",
      baseWriteSettings
    )
  ).async
    .runWith(Sink.seq)


  val numReqs = stream.via(Flows.Clfw2Lwr).via(Flows.Lwr2esIndex).via(
    ElasticsearchFlow.create[LogWindowedReqAlrm](
      "essink1",
      "_doc",
      baseWriteSettings
    )
  ).async
    .runWith(Sink.seq)


  val esSink = ElasticsearchSink.create[LogWindowedHost](
    indexName = essinkIndex,
    typeName = "_doc"
  )
  

  val esSink1 = ElasticsearchSink.create[LogWindowedReqAlrm](
    indexName = essink1Index,
    typeName = "_doc"
  )
  
}

