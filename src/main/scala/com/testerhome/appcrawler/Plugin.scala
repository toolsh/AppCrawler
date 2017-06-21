package com.testerhome.appcrawler

/**
  * Created by seveniruby on 16/1/7.
  */
abstract class Plugin extends CommonLog{
  private var crawler: Crawler=_
  def getCrawler(): Crawler ={
    this.crawler
  }
  def setCrawer(crawler:Crawler): Unit ={
    this.crawler=crawler
  }
  def init(crawler: Crawler): Unit ={
    this.crawler=crawler
    log.addAppender(crawler.fileAppender)
    log.info(this.getClass.getName+" init")
  }
  def start(): Unit ={

  }
  def afterUrlRefresh(url:String): Unit ={

  }

  def beforeBack(): Unit ={

  }
  def beforeElementAction(element: URIElement): Unit ={

  }
  def afterElementAction(element: URIElement): Unit ={

  }

  /**
    * 如果实现了请设置返回值为true
    * @param path
    * @return
    */
  def screenshot(path:String): Boolean ={
    false
  }

  def getPageSource(): String ={
    ""
  }
  def stop(): Unit ={

  }
}
