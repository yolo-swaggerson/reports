package name.ted

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.joda.time.DateTime
import play.api.libs.json._
import play.api.libs.ws.WSClient
import play.api.libs.ws.ning.NingWSClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object App {
  case class Results(
    surveyBuildTime: Double,
    campaignBuildTime: Double,
    blah: String)

  object Results {
    implicit val format: OFormat[Results] = Json.format[Results]
  }

  case class BuildRecord(
    id: String,
    number: Long,
    result: String, //enumify?
    timestamp: Long,
    duration: Long
  )

  object BuildRecord {
    implicit val format: OFormat[BuildRecord] = Json.format[BuildRecord]
  }

  case class BuildRecords(
    builds: Seq[BuildRecord]
  )

  object BuildRecords {
    implicit val format: OFormat[BuildRecords] = Json.format[BuildRecords]
  }

  object JenkinsStatsHelper {
    def averageBuildTime(wsClient: WSClient, jobName: String)(implicit end: DateTime, lookback: Int): Future[Double] = {
      val fqu = s"https://rally-jenkins.werally.in/job/Builds/job/${jobName}/api/json"

      wsClient
        .url(fqu)
          .withQueryString("tree" -> "builds[number,id,timestamp,result,duration]{1,100}")
        .get
        .map { res =>
          val js = res.json

          val records: BuildRecords = js.as[BuildRecords]

          val pool = records.builds
            .filter(r => r.result == "SUCCESS" && end.minusDays(lookback).isBefore(r.timestamp))

          if (pool.isEmpty) {
            0.0D
          }
          else {
            pool.foldLeft(0L)((z, a) => {
              z + a.duration
            }).toDouble / pool.length.toDouble

          }
        }
    }
  }

  def main(args: Array[String]) {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()

    implicit val startTime = DateTime.now
    implicit val span = 7

    val wsClient = NingWSClient()

    val jenkinsSurveyFuture = JenkinsStatsHelper.averageBuildTime(wsClient, "survey")
    val jenkinsCampaignFuture = JenkinsStatsHelper.averageBuildTime(wsClient, "campaign")

    for {
      jenkinsSurvey <- jenkinsSurveyFuture
      jenkinsCampaign <- jenkinsCampaignFuture
    } {
      val sbt = jenkinsSurvey / (60 * 1000).toDouble
      val cbt = jenkinsCampaign / (60 * 1000).toDouble

      wsClient.close()

      print(Json.prettyPrint(Json.toJson(Results(sbt, cbt, "test"))))

//      println(Results(sbt, cbt, "test").toString)

      System.exit(0)
    }
  }
}

