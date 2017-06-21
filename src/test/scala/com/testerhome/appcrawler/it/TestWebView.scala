package com.testerhome.appcrawler.it

import java.net.URL

import com.testerhome.appcrawler.RichData
import io.appium.java_client.android.AndroidDriver
import org.openqa.selenium.{By, WebElement}
import org.openqa.selenium.remote.{RemoteWebDriver, DesiredCapabilities}
import org.scalatest.FunSuite
import collection.JavaConversions._

/**
  * Created by seveniruby on 16/11/3.
  */
class TestWebView extends FunSuite{

  test("webview"){
    val capability=new DesiredCapabilities()
    capability.setCapability("app", "")
    capability.setCapability("appPackage", "iHealth.AiJiaKang.MI")
    capability.setCapability("fastReset", "true")
    capability.setCapability("noReset", "false")
    capability.setCapability("automationName", "appium")
    capability.setCapability("deviceName", "ddd")
    capability.setCapability("platformName", "android")
    capability.setCapability("appActivity", "com.ihealth.aijiakang.ui.user.User_Welcome")

    //val url="http://192.168.100.65:7771"
    val url="http://127.0.0.1:4723/wd/hub"
    val driver=new AndroidDriver[WebElement](new URL(url), capability)
    Thread.sleep(3000)
    var xml=driver.getPageSource
    println(RichData.toPrettyXML(xml))
    driver.getContextHandles.toArray.foreach(x=>println(x))
    //driver.context("WEBVIEW_com.ihealthlabs.ijiankang.patient.android")
    val size=driver.manage().window().getSize
    driver.swipe( (size.width*0.9).toInt, (size.height*0.5).toInt,
      (size.width*0.1).toInt, (size.height*0.5).toInt, 2000)

    driver.swipe( (size.width*0.9).toInt, (size.height*0.5).toInt,
      (size.width*0.1).toInt, (size.height*0.5).toInt, 2000)

    driver.swipe( (size.width*0.9).toInt, (size.height*0.5).toInt,
      (size.width*0.1).toInt, (size.height*0.5).toInt, 2000)

    xml=driver.getPageSource
    println(RichData.toPrettyXML(xml))
    driver.getContextHandles.toArray.foreach(x=>println(x))

    driver.findElements(By.xpath("//*")).foreach(x=>{
      println(x.getText)
      println(x.getTagName)
      println(x.getLocation)
      println(x.getAttribute("text"))
      println(x.getAttribute("value"))
      println(x.getAttribute("name"))
      println(x.getAttribute("id"))
      println(x.getAttribute("class"))
      println(x.getAttribute("type"))
      println(x.getAttribute("placeholder"))
      println("============")
    })

    driver.findElementByXPath("//*[text()='立即体验']")


    driver.context("WEBVIEW_com.android.browser")

    driver.findElement(By.tagName("input")).sendKeys("18201578100")
    driver.findElement(By.tagName("button")).click()
    Thread.sleep(3000)




    driver.findElement(By.xpath("//input[@type='password']")).sendKeys("12344321")
    Thread.sleep(1000)

    println("ddd")
    driver.findElements(By.xpath("//*[contains(text(), '录')]")).foreach(x=>{
      println(x.getText)
      println(x.getTagName)
      println(x.getLocation)
      println(x.getAttribute("text"))
      println(x.getAttribute("value"))
      println(x.getAttribute("name"))
      println(x.getAttribute("id"))
      println(x.getAttribute("class"))
      println(x.getAttribute("type"))
      println(x.getAttribute("placeholder"))
      println("============")
    })

    println("ddd")
    driver.findElements(By.xpath("//button")).foreach(x=>{
      println(x.getText)
      println(x.getTagName)
      println(x.getLocation)
      println(x.getAttribute("text"))
      println(x.getAttribute("value"))
      println(x.getAttribute("name"))
      println(x.getAttribute("id"))
      println(x.getAttribute("class"))
      println(x.getAttribute("type"))
      println(x.getAttribute("placeholder"))
      println(x.getAttribute("innerText"))
      println("============")
    })

    driver.findElement(By.xpath("//*[contains(text(), '录')]")).click()
    Thread.sleep(1000)

    println("ddd")
    driver.findElements(By.xpath("//*[contains(., '健康')]")).foreach(x=>{
      println(x.getText)
      println(x.getTagName)
      println(x.getLocation)
      println(x.getAttribute("text"))
      println(x.getAttribute("value"))
      println(x.getAttribute("name"))
      println(x.getAttribute("id"))
      println(x.getAttribute("class"))
      println(x.getAttribute("type"))
      println(x.getAttribute("placeholder"))
      println(x.getAttribute("innerText"))
      println("============")
    })


    Thread.sleep(2000)







  }

}
