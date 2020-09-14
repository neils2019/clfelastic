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

import akka.stream.scaladsl._
import akka.stream.alpakka.elasticsearch._
import akka.stream.alpakka.elasticsearch.scaladsl._

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.util.ByteString
import akka.{Done,NotUsed}

import java.util.Date
import scala.concurrent.{Future,Await}
import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory
import scala.language.postfixOps

/**
 * A real time streaming reactive design to monitor Network traffic in Common Log File Format, CLF:
 * <p>
 * Flows object containing 
 * stream Flow components for processing web server logs in common log format.
 * Each Flow component transforms the data stream through either filtering,
 * function specific transformation or aggregation.
 * <p>
 * {{{
 * 
 *   val stream = FileIO.fomPath(file).via(Flows.bs2delimited).via(Flows.delimited2Clf).async.via(Flows.Clf2Clfw)
 *                  .stream.via(Flows.Clfw2Lwh).via(Flows.Lwh2esIndex)
 *                 .via(ElasticsearchFlow
 *                 .create[LogWindowedHost]("essink","_doc",baseWriteSettings)
 *                  ).async
 *                 .runWith(Sink.seq)
 * 
 *    
 * Components represent akka flows connected from Source to Sink forming 
 * data pipeline.  
 *  Source --> Flow transformation --> ... --> Sink       
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
object Flows {

  implicit val system = ActorSystem("FileClf")  
  implicit val actorMaterializer = ActorMaterializer()

  val MAXGROUPS = 1000
  val config = ConfigFactory.load()
  val windowInterval = config.getInt("app.window_interval_time") seconds
  val windowTimeout = config.getInt("app.window_timeout") seconds
  val alrmLevel = config.getInt("app.critical_request_volume")

  def aggrWinByHost(x:Seq[LogEntry]) = {

    val source = Source(x)
    source
      .map((x) => {
        x.host
      })
      .groupBy(MAXGROUPS,identity)
      .map(_ -> 1)    
      .reduce((l,r) => (l._1,l._2+r._2))
      .mergeSubstreams
      .map(x => {
        ByteString(s"[${x._1} => ${x._2}]\n")
        val now = new Date
        LogWindowedHost(now.getTime(),x._1,x._2.toInt)
      })
    .runWith(Sink.seq[LogWindowedHost])

  }


  def cntNumReqs(x: Seq[LogEntry]) = {
    val source = Source(x)
    source
      .map(_ => 1)
      .runWith(Sink.fold[Int,Int](0)(_+_))

  }

  val bs2delimited: Flow[ByteString, ByteString,NotUsed] =
    Framing.delimiter(
      ByteString("\n"),
      maximumFrameLength = 256,
      allowTruncation = true
    )

  var isStart: Boolean = true
  var cur: Date = new Date
  var prev: Date = new Date


  val delimited2Clf = {
    Flow[ByteString].map(_.utf8String)
      .map((x) => {
        CLF.getLogEntry(x)})
      .map((x) => {
        cur = CLF.toDate(x.date)
        if (isStart) {
          prev = cur
          isStart = false
        }
        val diff = cur.getTime() - prev.getTime()
        prev = cur
        //println(diff)
        val end = System.currentTimeMillis() + diff
        while (end-System.currentTimeMillis() > 0) {}
        x
      })
  }

  val Clf2Clfw = {
    Flow[LogEntry].groupedWithin(MAXGROUPS,windowInterval)
  }


  val Clfw2Lwh = {
      Flow[Seq[LogEntry]].map( (x) => {
        Await.result(aggrWinByHost(x),windowTimeout)
      })
  }


  val Clfw2Lwr = {
      Flow[Seq[LogEntry]].map( (x) => {
        val now = new Date
        val num = Await.result(cntNumReqs(x),windowTimeout)
        if (num < alrmLevel)
          LogWindowedReqAlrm(now.getTime(),num)
        else
          LogWindowedReqAlrm(now.getTime(),num,"Alrm")

      })
  }
  

  val Lwh2esIndex = {
    Flow[Seq[LogWindowedHost]]
      .mapConcat(identity)
      .map ( (x) => {
        WriteMessage.createIndexMessage(java.util.UUID.randomUUID.toString+"+"+x.time.toString, x)
      })
  }

  val Lwr2esIndex = {
    Flow[LogWindowedReqAlrm]
      .map ( (x) => {
        WriteMessage.createIndexMessage(java.util.UUID.randomUUID.toString+"+"+x.time.toString, x)
      })
  }
}
