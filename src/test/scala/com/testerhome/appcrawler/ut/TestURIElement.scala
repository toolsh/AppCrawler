package com.testerhome.appcrawler.ut

import com.testerhome.appcrawler.URIElement
import org.scalatest.FunSuite

/**
  * Created by seveniruby on 16/9/29.
  */
class TestURIElement extends FunSuite {
  test("windows file name"){
    val element=URIElement("", "", "", "", "//xxfxx[@index=\"11\" and @text=\"fff<xxx->>>dddff\"]")
    println(element.toFileName())
  }

  test("tag path"){

    val element=URIElement("", "", "", "", "//xxfxx[@index=\"11\" and @index=\"2\" and  @text=\"fff<xxx->>>dddff\"]")
    println(element.toTagPath())
  }

}
