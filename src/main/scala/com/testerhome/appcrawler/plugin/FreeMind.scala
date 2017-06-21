package com.testerhome.appcrawler.plugin

import com.testerhome.appcrawler.{TreeNode, Plugin}

import scala.collection.mutable.ListBuffer
import scala.reflect.io.File

/**
  * Created by seveniruby on 16/9/19.
  */
class FreeMind extends Plugin{

  private val elementTree = TreeNode[String]("AppCrawler")
  private val elementTreeList = ListBuffer[String]()

  override def stop(): Unit ={
    log.info(s"genereate freemind file freemind.mm")
    report()
  }

  def report(): Unit ={
    getCrawler().store.clickedElementsList.foreach(element=>{
      elementTreeList.append(element.url)
      elementTreeList.append(element.loc)
    })

    File(s"${getCrawler().conf.resultDir}/freemind.mm").writeAll(
      elementTree.generateFreeMind(elementTreeList)
    )
  }

}
