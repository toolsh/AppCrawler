package com.testerhome.appcrawler

import java.io.OutputStreamWriter

import com.fasterxml.jackson.annotation.JsonIgnore
import org.apache.log4j._


/**
  * Created by seveniruby on 16/3/31.
  */
trait CommonLog {
  BasicConfigurator.configure()
  Logger.getRootLogger.setLevel(Level.INFO)
  @JsonIgnore
  val layout=new PatternLayout("%d{yyyy-MM-dd HH:mm:ss} %p [%c{1}.%M.%L] %m%n")
  @JsonIgnore
  lazy val log = initLog()

  def initLog(): Logger ={
    val log = Logger.getLogger(this.getClass.getName)
    //val log=Logger.getRootLogger
    if(log.getAppender("console")==null){
      val console=new ConsoleAppender()
      console.setName("console")
      console.setWriter(new OutputStreamWriter(System.out))
      console.setLayout(layout)
      log.addAppender(console)
    }else{
      log.info("alread exist")
    }
    log.trace(s"set ${this} log level to ${GA.logLevel}")
    log.setLevel(GA.logLevel)
    log.setAdditivity(false)
    log
  }
}
