package com.testerhome.appcrawler.plugin

import java.util.logging.Level

import com.testerhome.appcrawler.driver.AppiumClient
import com.testerhome.appcrawler.{Plugin, URIElement}

import scala.collection.mutable.ListBuffer
import scala.reflect.io.File

/**
  * Created by seveniruby on 16/1/21.
  *
  * 如果某种类型的控件点击次数太多, 就跳过. 设定一个阈值
  */
class LogPlugin extends Plugin {
  private var logs = ListBuffer[String]()
  val driver = getCrawler().driver.asInstanceOf[AppiumClient].driver

  override def afterElementAction(element: URIElement): Unit = {
    //第一次先试验可用的log 后续就可以跳过从而加速
    if (logs.isEmpty) {
      driver.manage().logs().getAvailableLogTypes.toArray().foreach(logName => {
        log.info(s"read log=${logName.toString}")
        try {
          saveLog(logName.toString)
          logs += logName.toString
        } catch {
          case ex: Exception => log.warn(s"log=${logName.toString} not exist")
        }
      })
    }
    if(getCrawler().getElementAction()!="skip") {
      logs.foreach(log => {
        saveLog(log)
      })
    }
  }

  def saveLog(logName:String): Unit ={
    log.info(s"read log=${logName.toString}")
    val logMessage = driver.manage().logs.get(logName.toString).filter(Level.ALL).toArray()
    log.info(s"log=${logName} size=${logMessage.size}")
    if (logMessage.size > 0) {
      val fileName = getCrawler().getBasePathName()+".log"
      log.info(s"save ${logName} to $fileName")
      File(fileName).writeAll(logMessage.mkString("\n"))
      log.info(s"save ${logName} end")
    }
  }


  override def afterUrlRefresh(url: String): Unit = {

  }
  override def stop(): Unit ={
    logs.foreach(log => {
      saveLog(log)
    })
  }

}
