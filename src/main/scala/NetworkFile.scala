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

import java.util.Date
import java.time.LocalDateTime
import java.util.regex.{Pattern,Matcher}
import java.util.{Date}
import java.text.SimpleDateFormat

import scala.language.postfixOps
import scala.concurrent.{Await,Future}
import scala.concurrent.duration._
import akka.util.ByteString

// common log format
/**
 * Common log format object.
 * {{{
 *  csv header: host,ignore,user,date,request,status,size
 *   199.72.81.55 - - [01/Jul/1995:00:00:01 -0400] "GET /history/apollo/ HTTP/1.0" 200 6245
 *
 * import org.nj.clfmon.CLF
 *  streaming with bytestream x =>
 * .map((x) = > {
 *  cur = CLF.getLogEntry(x)
 * })
 *  
 *   converts bytestream from csv log file stream to a LogEntry case class    
 * 
 *  see Flows.delimited2Clf for example stream function transforming bytestream to LogEntry
 * }}}
 */
object CLF {
  val HOST = 1
  val IGNORE = 2
  val USER = 3
  val DATE = 4
  val REQUEST = 5
  val STATUS = 6
  val SIZE = 7

  val datePattern = "dd/MMM/yyyy:HH:mm:ss Z"

  val EntryRegEx: String = """([^ ]*) ([^ ]*) ([^ ]*) \[([^]]*)\] "([^"]*)" ([^ ]*) ([^ ]*)"""

  /**
   * Returns a new Date object from date string, eg. '01/Jul/1995:00:00:01 -0400'
   * @param s The string data value from CLF date entry
   * @return java Date object
   */
  def toDate(s: String): Date = {
    val d = new SimpleDateFormat(CLF.datePattern)
    return d.parse(s)
  }

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  val p = Pattern.compile(EntryRegEx)


  /**
   * Returns a new LogEntry case class object from common log file, CLF, entry.
   *  case class LogEntry(host: String,ignore: String,user: String,date: String,request: String, status: String, size: Int)
   * @param line The string CLF entry - host,ignore,user,date,request,status,size
   * @return LogEntry object
   */
  def getLogEntry(line: String): LogEntry = {
   
    val matcher = p.matcher(line)
    matcher.find
    //println("here"+ matcher.find)
    //println(matcher.group(CLF.HOST))
    val sz: Int = matcher.group(CLF.SIZE) match {
      case "-" => 0
      case _ => matcher.group(CLF.SIZE).toInt
    }

    val ne = LogEntry(matcher.group(CLF.HOST),matcher.group(CLF.IGNORE),matcher.group(CLF.USER),matcher.group(CLF.DATE),matcher.group(CLF.REQUEST),matcher.group(CLF.STATUS),sz)
    //println(ne)
    ne
  }

}

  /**
   * LogEntry object.  Case class encapsulation for common log format, CLF, entry
   * {{{
   * CLF - host,ignore,user,date,request,status,size 
   * }}}
   */  
  case class LogEntry(host: String,ignore: String,user: String,date: String,request: String, status: String, size: Int)
  //val EntryRegEx: String = """([^ ]*) ([^ ]*) ([^ ]*) \[([^]]*)\] "([^"]*)" ([^ ]*) ([^ ]*)"""

  /**
   * LogWindowedHost object.  Case class encapsulation for aggregated req per host over time window
   *  LogWindowedHost: time, host, number of requests <= aggregated requests per host within window
   */  
  case class LogWindowedHost(time: Long, host:String,numReq:Int)

  /**
   * LogWindowedReq object.  Case class encapsulation for aggregated total req over time window 
   *  LogWindowedReq: time, number of requests <= aggregated total requests within window
   */  
  case class LogWindowedReq(time: Long, numReq:Int)

  /**
   * LogWindowedReqAlrm object.  Case class encapsulation for aggregated total req over time window with alarm. 
   *  LogWindowedReqAlrm: time, number of requests, alarm <= aggregated total requests within window marked with alarm if number of requests over threshold
   */  
  case class LogWindowedReqAlrm(time: Long, numReq: Int,alrm: String="Normal")  
//}
