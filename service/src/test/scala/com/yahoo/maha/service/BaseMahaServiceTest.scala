package com.yahoo.maha.service

import java.nio.file.{Files, Paths}
import java.util.UUID

import com.google.common.io.Closer
import com.yahoo.maha.core.DailyGrain
import com.yahoo.maha.core.ddl.OracleDDLGenerator
import com.yahoo.maha.jdbc.JdbcConnection
import com.yahoo.maha.service.utils.MahaConstants
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import org.apache.log4j.MDC
import org.joda.time.{DateTime, DateTimeZone}
import org.scalatest.FunSuite

/**
 * Created by pranavbhole on 21/03/18.
 */
trait BaseMahaServiceTest extends FunSuite {
  protected var dataSource: Option[HikariDataSource] = None
  protected var jdbcConnection: Option[JdbcConnection] = None
  protected val closer = Closer.create()

  final val REGISTRY = "er"
  protected[this] val fromDate = DailyGrain.toFormattedString(DateTime.now(DateTimeZone.UTC).minusDays(7))
  protected[this] val toDate = DailyGrain.toFormattedString(DateTime.now(DateTimeZone.UTC))

  val h2dbId = UUID.randomUUID().toString.replace("-","")

  def initJdbcToH2(): Unit = {
    val config = new HikariConfig()
    config.setJdbcUrl(s"jdbc:h2:mem:$h2dbId;MODE=Oracle;DB_CLOSE_DELAY=-1")
    config.setUsername("sa")
    config.setPassword("h2.test.database.password")
    config.setMaximumPoolSize(1)
    dataSource = Option(new HikariDataSource(config))
    jdbcConnection = dataSource.map(new JdbcConnection(_))
  }

  initJdbcToH2()

  val path = Paths.get(getUserDir + "/src/test/resources/mahaServiceExampleJson.json")

  val mahaServiceResult = MahaServiceConfig
    .fromJson(scala.io.Source.fromFile(path.toString)
    .getLines()
    .mkString
      .replaceAll("H2DBID", h2dbId)
      .getBytes("utf-8"))

  assert(mahaServiceResult.isSuccess)

  val mahaServiceConfig = mahaServiceResult.toOption.get
  val mahaService = DefaultMahaService(mahaServiceConfig)

  //For Kafka Logging init
  MDC.put(MahaConstants.REQUEST_ID, "123Request")
  MDC.put(MahaConstants.USER_ID,"abc")

  assert(mahaServiceConfig.registry.get("er").isDefined)
  val erRegistryConfig = mahaServiceConfig.registry.get("er").get
  val erRegistry= erRegistryConfig.registry
  assert(erRegistry.isCubeDefined("student_performance"))
  assert(erRegistry.getDimension("student").isDefined)

  val ddlGenerator = new OracleDDLGenerator
  assert(jdbcConnection.isDefined)

  protected[this] def getUserDir : String = {
    val userDir = System.getProperty("user.dir")
    s"$userDir"
  }

  def createTables(): Unit = {
    // Create Tables
    erRegistry.factMap.values.foreach {
      publicFact =>
        publicFact.factList.foreach {
          fact=>
            val ddl = ddlGenerator.toDDL(fact)
            assert(jdbcConnection.get.executeUpdate(ddl).isSuccess)
        }
    }
    erRegistry.dimMap.values.foreach {
      publicDim =>
        publicDim.dimList.foreach {
          fact=>
            val ddl = ddlGenerator.toDDL(fact)
            assert(jdbcConnection.get.executeUpdate(ddl).isSuccess)
        }
    }
  }



}
