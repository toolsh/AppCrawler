package com.testerhome.appcrawler.it

import org.scalatest.{BeforeAndAfterAll, FunSuite}
import org.apache.log4j.Logger
import com.alibaba.fastjson.JSONObject
import macaca.client.MacacaClient


/**
  * Created by seveniruby on 2017/4/17.
  */
class TestMacaca extends FunSuite with BeforeAndAfterAll{

  val driver=new MacacaClient()
  override def beforeAll(): Unit = {

    val porps = new JSONObject()
    porps.put("autoAcceptAlerts", true)
    porps.put("browserName", "")
    porps.put("platformName", "android")
    porps.put("package", "com.gotokeep.keep")
    porps.put("activity", ".activity.SplashActivity")
    porps.put("reuse", 3)

    val desiredCapabilities = new JSONObject()
    desiredCapabilities.put("desiredCapabilities", porps)
    driver.initDriver(desiredCapabilities)

  }
  test("macaca android"){
    println(driver.source())
  }
  test("macaca chrome"){
    val porps = new JSONObject()
    porps.put("autoAcceptAlerts", true)
    porps.put("browserName", "Chrome")
    porps.put("platformName", "desktop") // android or ios

    porps.put("javascriptEnabled", true)
    porps.put("platform", "ANY")

    val desiredCapabilities = new JSONObject()
    desiredCapabilities.put("desiredCapabilities", porps)
    driver.initDriver(desiredCapabilities)
    driver.get("http://www.baidu.com/")
  }

}
