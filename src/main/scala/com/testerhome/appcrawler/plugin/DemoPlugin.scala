package com.testerhome.appcrawler.plugin

import com.testerhome.appcrawler.URIElement
import com.testerhome.appcrawler.Plugin

/**
  * Created by seveniruby on 16/1/21.
  */
class DemoPlugin extends Plugin{
  override def beforeElementAction(element: URIElement): Unit ={
    log.info("demo com.testerhome.appcrawler.plugin before element action")
    log.info(element)
    log.info("demo com.testerhome.appcrawler.plugin end")
  }
  override def afterUrlRefresh(url:String): Unit ={
    getCrawler().currentUrl=url.split('|').last
    log.info(s"new url=${getCrawler().currentUrl}")
    if(getCrawler().currentUrl.contains("Browser")){
      getCrawler().getBackButton()
    }
  }

}
