package com.testerhome.appcrawler

import java.io
import java.util.Date

import com.testerhome.appcrawler.driver.AppiumClient
import com.testerhome.appcrawler.driver.{MacacaDriver, WebDriver}
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.log4j._
import org.scalatest
import org.scalatest.ConfigMap
import sun.misc.{Signal, SignalHandler}

import scala.annotation.tailrec
import scala.collection.mutable.{ListBuffer, Map}
import scala.collection.{immutable, mutable}
import scala.reflect.io.File
import scala.util.{Failure, Success, Try}


/**
  * Created by seveniruby on 15/11/28.
  */
class Crawler extends CommonLog {
  var driver:WebDriver=_
  var conf = new CrawlerConf()

  /** 存放插件类 */
  val pluginClasses = ListBuffer[Plugin]()
  var fileAppender: FileAppender = _

  val store = new URIElementStore
  /** 元素的默认操作 */
  private var currentElementAction = "click"

  var appName = ""
  /** 当前的url路径 */
  var currentUrl = ""

  var isRefreshSuccess = true

  private var backRetry = 0
  //最大重试次数
  var backMaxRetry = 5
  private var swipeRetry = 0
  //滑动最大重试次数
  var swipeMaxRetry = 2
  var stopAll = false
  var signalInt = 0
  private val startTime = new Date().getTime

  val urlStack = mutable.Stack[String]()

  protected val backDistance = new DataRecord()
  val appNameRecord = new DataRecord()
  protected val contentHash = new DataRecord

  /**
    * 根据类名初始化插件. 插件可以使用java编写. 继承自Plugin即可
    */
  def loadPlugins(): Unit = {
    //todo: 需要考虑默认加载一些插件,并防止重复加载
    val defaultPlugins = List(
      "com.testerhome.appcrawler.plugin.TagLimitPlugin",
      "com.testerhome.appcrawler.plugin.ReportPlugin",
      "com.testerhome.appcrawler.plugin.FreeMind"
    )
    defaultPlugins.foreach(name => pluginClasses.append(Class.forName(name).newInstance().asInstanceOf[Plugin]))

    conf.pluginList.foreach(name => {
      if (defaultPlugins.forall(_ != name)) {
        log.info(s"load com.testerhome.appcrawler.plugin $name")
        pluginClasses.append(Class.forName(name).newInstance().asInstanceOf[Plugin])
      }
    })

    //todo: 放到根目录有bug. 需要解决
    val dynamicPluginDir = (new java.io.File(getClass.getProtectionDomain.getCodeSource.getLocation.getPath))
      .getParentFile.getCanonicalPath + File.separator + "plugins" + File.separator
    log.info(s"dynamic load plugin in ${dynamicPluginDir}")
    val dynamicPlugins = Runtimes.loadPlugins(dynamicPluginDir)
    log.info(s"found dynamic plugins size ${dynamicPlugins.size}")
    dynamicPlugins.foreach(pluginClasses.append(_))
    pluginClasses.foreach(log.info)
    pluginClasses.foreach(p => p.init(this))
    pluginClasses.foreach(p => p.start())
  }

  /**
    * 加载配置文件并初始化
    */
  def loadConf(crawlerConf: CrawlerConf): Unit = {
    conf = crawlerConf
    log.setLevel(GA.logLevel)
  }

  def loadConf(file: String): Unit = {
    conf = new CrawlerConf().load(file)
    log.setLevel(GA.logLevel)
  }

  def addLogFile(): Unit = {
    AppCrawler.logPath = conf.resultDir + "/appcrawler.log"

    fileAppender = new FileAppender(layout, AppCrawler.logPath, false)
    log.addAppender(fileAppender)

    val resultDir = new java.io.File(conf.resultDir)
    if (!resultDir.exists()) {
      FileUtils.forceMkdir(resultDir)
      log.info("result dir path = " + resultDir.getAbsolutePath)
    }

  }

  /**
    * 启动爬虫
    */
  def start(existDriver: WebDriver = null): Unit = {
    addLogFile()
    log.debug("crawl config")
    log.debug(conf.toYaml())
    loadPlugins()
    if (existDriver == null) {
      log.info("prepare setup Appium")
      setupAppium()
      //driver.getAppStringMap
    } else {
      log.info("use existed driver")
      this.driver = existDriver
    }
    log.info(s"platformName=${conf.currentDriver} driver=${driver}")
    log.info(AppCrawler.banner)
    log.info("waiting for app load")
    Thread.sleep(8000)
    log.info(s"driver=${existDriver}")
    log.info("get screen info")
    driver.getDeviceInfo()
    refreshPage()


    //todo: 不是所有的实现都支持
    //driver.manage().logs().getAvailableLogTypes().toArray.foreach(log.info)
    //设定结果目录
    runStartupScript()
    conf.appWhiteList.append(appNameRecord.last().toString)

    if(conf.testcase!=null){
      log.info("run steps")
      runSteps()
    }else{
      log.info("no testcase")
    }

    if(conf.autoCrawl){
      crawl(conf.maxDepth)
    }else{
      log.info("autoCrawl=false")
    }

  }

  def crawl(depth: Int): Unit ={
    //清空堆栈 开始重新计数
    conf.maxDepth=depth
    urlStack.clear()
    refreshPage()
    handleCtrlC()

    var retryCount=1
    while(retryCount>0) {
      Try(crawl()) match {
        case Success(v) => {
          log.info("crawl finish")

          //如果错误太多就重试, 错误少就认为是完成了


          log.info("list last 10 exec results")
          driver.appiumExecResults.takeRight(10).foreach(x=>log.info(x))

          //todo: 需要继续过滤appium的报错
          val failCount10=driver.appiumExecResults.takeRight(10).filter(_!="success").size
          log.info(s"failCount=${failCount10} retryCount=${retryCount}")
          if(failCount10>10*0.3 && retryCount<10){
            log.error("appium error, restart and continue to crawl ")
            retryCount+=1
            restart()
          }else{
            log.info("success finish")
            retryCount=0
          }
        }
        case Failure(e) => {
          log.error("crawl not finish, return with exception")
          log.error(e.getLocalizedMessage)
          log.error(ExceptionUtils.getRootCauseMessage(e))
          log.error(ExceptionUtils.getRootCause(e))
          ExceptionUtils.getRootCauseStackTrace(e).foreach(log.error)
          log.error("create new session")

          retryCount+=1
          restart()
        }
      }

    }

    //爬虫结束
    stop()

  }

  def restart(): Unit = {
    log.info("restart appium")
    backRetry=0
    log.info("set app to null to restart appium")
    conf.capability ++= Map("app"->"")
    setupAppium()
    refreshPage()
    doElementAction(URIElement(s"${currentUrl}", "", "", "",
      s"restart-${store.clickedElementsList.size}"), "")
  }

  def runStartupScript(): Unit = {
    log.info("first refresh")
    doElementAction(URIElement(s"${currentUrl}", "", "", "",
      s"startupActions-Start-${store.clickedElementsList.size}"), "")

  }

  def runSteps(): Unit ={
    log.info("run testcases")
    new AutomationSuite().execute("run steps", ConfigMap("crawler"->this))
  }

  def setupAppium(): Unit = {
    //driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS)
    //PageFactory.initElements(new AppiumFieldDecorator(driver, 10, TimeUnit.SECONDS), this)
    //implicitlyWait(Span(10, Seconds))

    //todo: 主要做遍历测试和异常测试. 所以暂不使用selendroid
    //todo: Appium模式太慢

    val url=conf.capability("appium").toString
    conf.capability.getOrElse("automationName", "").toString match {
      case "macaca" => {
        log.info("use macaca")
        driver=new MacacaDriver(url, conf.capability)
      }
      case _ => {
        log.info("use AppiumClient")
        driver=new AppiumClient(url, conf.capability)
      }
    }

    GA.log(conf.capability.getOrElse("appPackage", "")+conf.capability.getOrElse("bundleId", "").toString)

  }

  def md5(format: String) = {
    //import sys.process._
    //s"echo ${format}" #| "md5" !

    //new java.lang.String(MessageDigest.getInstance("MD5").digest(format.getBytes("UTF-8")))
    java.security.MessageDigest.getInstance("MD5").digest(format.getBytes("UTF-8")).map(0xFF & _).map {
      "%02x".format(_)
    }.foldLeft("") {
      _ + _
    }
  }


  /**
    * 判断内容是否变化
    *
    * @return
    */
  def getContentHash(): String = {
    //var nodeList = driver.getListFromXPath("//*[not(ancestor-or-self::UIATableView)]")
    //nodeList = nodeList intersect driver.getListFromXPath("//*[not(ancestor-or-self::android.widget.ListView)]")

    //排除iOS状态栏 android不受影响
    val nodeList = driver.getListFromXPath("//*[not(ancestor-or-self::UIAStatusBar)]")
    val schemaBlackList = List()
    //加value是考虑到某些tab, 他们只有value不同
    //<             label="" name="分时" path="/0/0/6/0/0" valid="true" value="1"
    //---
    //>             label="" name="分时" path="/0/0/6/0/0" valid="true" value="0"
    md5(nodeList.filter(node => !schemaBlackList.contains(node("tag"))).
      map(node => node.getOrElse("xpath", "")
        + node.getOrElse("value", "").toString
        + node.getOrElse("selected", "").toString
        + node.getOrElse("text", "").toString
      ).
      mkString("\n"))
  }

  /**
    * 获得布局Hash
    */
  def getSchema(): String = {
    val nodeList = driver.getListFromXPath("//*[not(ancestor-or-self::UIAStatusBar)]")
    md5(nodeList.map(getUrlElementByMap(_).toTagPath()).distinct.mkString("\n"))
  }

  def getUrl(): String = {
    val baseUrl=if (conf.defineUrl.nonEmpty) {
      val urlString = conf.defineUrl.flatMap(driver.getListFromXPath(_)).distinct.map(x => {
        //按照attribute, label, name顺序挨个取第一个非空的指
        log.debug(x)
        if (x.contains("attribute")) {
          x.getOrElse("attribute", "")
        } else {
          if (x.get("label").nonEmpty) {
            x.getOrElse("label", "")
          } else {
            x.getOrElse("name", "")
          }
        }
      }).filter(_.toString.nonEmpty).mkString("-")
      log.info(s"defineUrl=$urlString")
      urlString
    }else{
      ""
    }
    List(driver.getAppName(), driver.getUrl(), baseUrl).distinct.filter(_.nonEmpty).mkString("-")

  }

  /**
    * 获取控件的基本属性并设置一个唯一的uid作为识别. screenName+id+name
    *
    * @param x
    * @return
    */
  def getUrlElementByMap(x: immutable.Map[String, Any]): URIElement = {
    //控件的类型
    val tag = x.getOrElse("tag", "NoTag").toString

    //name为Android的description/text属性, 或者iOS的value属性
    //appium1.5已经废弃findElementByName
    val name = x.getOrElse("value", "").toString.replace("\n", "\\n").take(30)
    //name为id/name属性. 为空的时候为value属性

    //id表示android的resource-id或者iOS的name属性
    val id = x.getOrElse("name", "").toString.split('/').last
    val loc = x.getOrElse("xpath", "").toString
    URIElement(currentUrl, tag, id, name, loc)
  }

  def isBackApp(): Boolean = {
    //跳到了其他app. 排除点一次就没有的弹框
    if (conf.appWhiteList.forall(appNameRecord.last().toString.matches(_)==false) && appNameRecord.last(2).distinct.size>1) {
      log.warn(s"not in app white list ${conf.appWhiteList}")
      log.warn(s"jump to other app appName=${appNameRecord.last()} lastAppName=${appNameRecord.pre()}")
      setElementAction("backApp")
      return true
    } else {
      return false
    }

  }

  def isExit(): Boolean = {
    //超时退出
    if ((new Date().getTime - startTime) > conf.maxTime * 1000) {
      log.warn("maxTime out Quit need exit")
      return true
    }
    if (backRetry > backMaxRetry) {
      log.warn(s"backRetry ${backRetry} > backMaxRetry ${backMaxRetry} need exit")
      return true
    }

    if (appNameRecord.last(5).forall(conf.appWhiteList.contains(_) == false)) {
      log.error(s"appNameRecord last 5 ${appNameRecord.last(5)}")
      return true
    }
    return false
  }

  def isReturn(): Boolean = {
    //url黑名单
    if (conf.urlBlackList.exists(urlStack.head.matches(_))) {
      log.warn(s"${urlStack.head} in urlBlackList should return")
      return true
    }

    //url白名单, 第一次进入了白名单的范围, 就始终在白名单中. 不然就算不在白名单中也得遍历.
    //上层是白名单, 当前不是白名单才需要返回
    if (conf.urlWhiteList.size > 0
      && conf.urlWhiteList.filter(urlStack.head.matches(_)).isEmpty
      && conf.urlWhiteList.filter(urlStack.tail.headOption.getOrElse("").matches(_)).nonEmpty) {
      log.warn(s"${urlStack.head} not in urlWhiteList should return")
      return true
    }

    //app黑名单
    if (appName.matches(".*browser")) {
      log.warn(s"current app is browser, back")
      return true
    }
    //滚动多次没有新元素
    if (swipeRetry > swipeMaxRetry) {
      swipeRetry = 0
      log.warn(s"swipe retry too many times ${swipeRetry} > ${swipeMaxRetry}")
      return true
    }
    //超过遍历深度
    log.info(s"urlStack=${urlStack} baseUrl=${conf.baseUrl} maxDepth=${conf.maxDepth}")
    //大于最大深度并且是在进入过基础Url
    if (urlStack.length > conf.maxDepth) {
      log.warn(s"urlStack.depth=${urlStack.length} > maxDepth=${conf.maxDepth}")
      return true
    }
    /*
        //回到桌面了

        if (urlStack.filter(_.matches("Launcher.*")).nonEmpty || appName.matches("com.android\\..*")) {
          log.warn(s"maybe back to desktop ${urlStack.reverse.mkString("-")} need exit")
          //尝试后腿 而不是退出
          //needExit = true
          return true
        }
    */

    false

  }

  def isValid(m: immutable.Map[String, Any]): Boolean = {
    m.getOrElse("visible", "true") == "true" &&
      m.getOrElse("enabled", "true") == "true" &&
      m.getOrElse("valid", "true") == "true"
  }

  def isSmall(m: immutable.Map[String, Any]): Boolean ={
    if(m.getOrElse("bounds", "").toString.nonEmpty){
      val bounds="\\d+".r().findAllIn(m.get("bounds").get.toString).matchData.map(_.group(0)).toList
      val width=bounds(2).toInt-bounds(0).toInt
      val height=bounds(3).toInt-bounds(1).toInt
      //高度小就跳过
      if(height<50){
        true
      }else{
        false
      }
    }else{
      false
    }
  }

  //todo: 支持xpath表达式
  def getSelectedNodes(): List[immutable.Map[String, Any]] = {
    var all = List[immutable.Map[String, Any]]()
    var firstElements = List[immutable.Map[String, Any]]()
    var lastElements = List[immutable.Map[String, Any]]()
    var selectedElements = List[immutable.Map[String, Any]]()
    var blackElements = List[immutable.Map[String, Any]]()

    val allElements = driver.getListFromXPath("//*")
    log.trace(s"all node size = ${allElements.size}")
    allElements.foreach(log.trace)

    conf.selectedList.foreach(xpath => {
      log.trace(s"selectedList xpath =  ${xpath}")
      val temp = driver.getListFromXPath(xpath).filter(isValid)
      selectedElements ++= temp
    })
    selectedElements=selectedElements.distinct
    log.info(s"selected nodes size = ${selectedElements.size}")
    selectedElements.map(_.getOrElse("xpath", "no xpath")).foreach(log.trace)

    //remove blackList
    conf.blackList.foreach(xpath => {
      log.info(s"blackList check xpath = ${xpath}")
      val temp = driver.getListFromXPath(xpath)
      temp.foreach(x=>
        log.info(s"blackList hit  ${x}")
      )
      blackElements ++= temp
    })
    blackElements=blackElements.distinct
    selectedElements = selectedElements diff blackElements
    log.info(s"all - black elements size = ${selectedElements.size}")
    selectedElements.map(_.getOrElse("xpath", "no xpath")).foreach(log.trace)

    //exclude small
    selectedElements=selectedElements.filter(isValid)
    log.info(s"all - small - non valid elements size = ${selectedElements.size}")
    selectedElements.map(_.getOrElse("xpath", "no xpath")).foreach(log.trace)

    //sort
    conf.firstList.foreach(xpath => {
      log.trace(s"firstList xpath = ${xpath}")
      val temp = driver.getListFromXPath(xpath).filter(isValid).intersect(selectedElements)
      firstElements ++= temp
    })
    log.trace("first elements")
    firstElements.map(_.getOrElse("xpath", "no xpath")).foreach(log.trace)

    conf.lastList.foreach(xpath => {
      log.trace(s"lastList xpath = ${xpath}")
      val temp = driver.getListFromXPath(xpath).filter(isValid).intersect(selectedElements)
      lastElements ++= temp
    })

    //去掉不在first和last中的元素
    selectedElements = selectedElements diff firstElements
    selectedElements = selectedElements diff lastElements

    //确保不重, 并保证顺序
    all = (firstElements ++ selectedElements ++ lastElements).distinct
    log.trace("sorted nodes")
    all.map(_.getOrElse("xpath", "no xpath")).foreach(log.trace)
    log.trace(s"sorted nodes length=${all.length}")

    //去掉back菜单
    all = all diff getBackNodes()
    log.info(s"all - backButton size=${all.length}")
    all.map(_.getOrElse("xpath", "no xpath")).foreach(log.trace)
    all
  }


  def hideKeyBoard(): Unit = {
    //iOS键盘隐藏
    if (driver.getListFromXPath("//UIAKeyboard").size >= 1) {
      log.info("find keyboard , just hide")
      driver.hideKeyboard()
    }
  }

  def refreshPage(): Boolean = {
    log.info("refresh page")
    driver.getPageSource()
    log.trace(driver.currentPageSource)

    if (driver.currentPageSource.nonEmpty) {
      parsePageContext()
      return true
    } else {
      log.warn("page source get fail, go back")
      setElementAction("back")
      return false

    }
    //appium解析pageSource有bug. 有些页面会始终无法dump. 改成解析不了就后退
  }

  def parsePageContext(): Unit = {
    appName = driver.getAppName()
    log.info(s"appName = ${appName}")
    appNameRecord.append(appName)

    currentUrl = getUrl()
    log.info(s"url=${currentUrl}")
    //如果跳回到某个页面, 就弹栈到特定的页面, 比如回到首页
    if (urlStack.contains(currentUrl)) {
      while (urlStack.head != currentUrl) {
        urlStack.pop()
      }
    } else {
      urlStack.push(currentUrl)
      if(urlStack.size>2) {
        saveLog()
      }
    }
    //判断新的url堆栈中是否包含baseUrl, 如果有就清空栈记录并从新计数
    if (conf.baseUrl.map(urlStack.head.matches(_)).contains(true)) {
      urlStack.clear()
      urlStack.push(currentUrl)
    }
    log.trace(s"urlStack=${urlStack}")
    //使用当前的页面. 不再记录堆栈.先简化

    //val contexts = MiniAppium.doAppium(driver.getContextHandles).getOrElse("")
    //val windows=MiniAppium.doAppium(driver.getWindowHandles).getOrElse("")
    //val windows = ""
    //log.trace(s"windows=${windows}")
    contentHash.append(getContentHash())
    log.info(s"currentContentHash=${contentHash.last()} lastContentHash=${contentHash.pre()}")
    if (contentHash.isDiff()) {
      log.info("ui change")
    } else {
      log.info("ui not change")
    }
    afterUrlRefresh()
  }

  def afterUrlRefresh(): Unit = {
    pluginClasses.foreach(p => p.afterUrlRefresh(currentUrl))
  }


  def beforeElementAction(element: URIElement): Unit = {
    log.trace("beforeElementAction")
    conf.beforeElementAction.foreach(elementAction => {
      val xpath = elementAction.get("xpath").get
      val action = elementAction.get("action").get
      if (driver.getListFromXPath(xpath).contains(element)) {
        Runtimes.eval(action)
      }
    })
    pluginClasses.foreach(p => p.beforeElementAction(element))
  }

  def afterElementAction(element: URIElement): Unit = {
    log.trace("afterElementAction")
    /*
    if (getElementAction() != "skip") {
      refreshPage()
    }
    */

    if (getElementAction() == "back" || getElementAction() == "backApp") {
      backRetry += 1
      log.info(s"backRetry=${backRetry}")
    } else {
      log.info(s"backRetry=0")
      backRetry = 0
    }

    log.info("afterElementAction eval")
    log.debug(conf.afterElementAction)
    conf.afterElementAction.foreach(Runtimes.eval)
    pluginClasses.foreach(p => p.afterElementAction(element))
  }

  def getElementAction(): String = {
    currentElementAction
  }

  /**
    * 允许插件重新设定当前控件的行为
    **/
  def setElementAction(action: String): Unit = {
    log.info(s"set action to ${action}")
    currentElementAction = action
  }

  def getBackNodes(): ListBuffer[immutable.Map[String, Any]] = {
    conf.backButton.flatMap(driver.getListFromXPath(_).filter(isValid))
  }

  def getBackButton(): Option[URIElement] = {
    log.info("go back")
    //找到可能的关闭按钮, 取第一个可用的关闭按钮
    getBackNodes().headOption match {
      case Some(v) if appNameRecord.isDiff() == false => {
        //app相同并且找到back控件才点击. 否则就默认back
        val element = getUrlElementByMap(v)
        val backElement=URIElement(
          element.url,
          element.tag,
          element.name,
          element.name+store.getClickedElementsList.map(_.loc==element.loc).size.toString,
          element.loc)
        setElementAction("click")
        backRetry+=1
        return Some(backElement)
      }
      case _ => {
        log.warn("find back button error")
        setElementAction("back")
        return Some(URIElement(s"${currentUrl}", "", "", "",
          s"Back-${store.clickedElementsList.size}"))
      }
    }
  }


  def getAvailableElement(): Seq[URIElement] = {
    //把元素转换为Element对象
    var allElements = getSelectedNodes().map(getUrlElementByMap(_))
    //获得所有未点击元素
    log.info(s"all elements size=${allElements.length}")

    //过滤已经被点击过的元素
    allElements = allElements.filter(!store.isClicked(_))
    log.info(s"all - clicked size=${allElements.size}")
    allElements.foreach(log.debug)

    allElements = allElements.filter(!store.isSkiped(_))
    log.info(s"fresh elements size=${allElements.length}")
    //记录未被点击的元素
    allElements.foreach(e => {
      log.debug(e)
      store.saveElement(e)
    })
    allElements
  }

  /**
    * 优化后的递归方法. 尾递归.
    * 刷新->找元素->点击第一个未被点击的元素->刷新
    */
  @tailrec
  final def crawl(): Unit = {
    log.info("\n\ncrawl next")
    //刷新页面
    var skipBeforeElementAction = true
    //是否需要退出或者后退, 得到要做的动作
    var nextElement: Option[URIElement] = None

    //todo: skip之后可以不用刷新
    if (getElementAction() == "skip") {
      log.info("skip refresh page because last action is skip")
      setElementAction("click")
    }

    //判断下一步动作

    //是否应该退出
    if (isExit()) {
      log.warn("get signal to exit")
      return
    }
    //页面刷新失败自动后退
    if (isRefreshSuccess == false) {
      log.warn("refresh fail")
      nextElement = Some(URIElement(s"${currentUrl}", "", "", "",
        s"Back-${store.clickedElementsList.size}"))
      setElementAction("back")
    } else {
      log.debug("refresh success")
    }

    //是否需要回退到app
    if (isBackApp()) {
      nextElement = Some(URIElement(s"${currentUrl}", "", "", "",
        s"backApp-${appNameRecord.last()}-${store.clickedElementsList.size}"))
      setElementAction("backApp")
    }

    //先应用优先规则
    if (nextElement == None) {
      //todo: 优化结构
      getElementByElementActions() match {
        case Some(e) => {
          log.info(s"found ${e} by ElementActions")
          nextElement = Some(e)
        }
        case None => {}
      }
    }

    //判断是否需要返回上层
    if (nextElement == None) {
      if (isReturn()) {
        log.info("need to back")
        nextElement = getBackButton()
        setElementAction("back")
      } else {
        log.info("no need to back")
      }
    }

    //查找正常的元素
    if (nextElement == None) {
      val allElements = getAvailableElement()
      allElements.headOption match {
        case Some(e) => {
          log.info(s"found ${e} by first available element")
          nextElement = Some(e)
          setElementAction(getActionFromNormalActions(e))
          skipBeforeElementAction = false
        }
        case None => {
          log.warn(s"${currentUrl} all elements had be clicked")
          setElementAction("back")
          nextElement = getBackButton()
          conf.afterUrlFinished.foreach(Runtimes.eval)
        }
      }
    }


    nextElement match {
      case Some(element) => {
        //todo: 输入情况 长按 需要考虑
        if(skipBeforeElementAction==false) {
          //决定是否需要跳过
          beforeElementAction(element)
        }else{
          log.info("skip beforeElementAction")
        }

        //不可和之前的判断合并
        if (getElementAction() == "skip") {
          log.trace(s"pop ${element}")
          store.setElementSkip(element)
        } else {
          //处理控件
          doElementAction(element, getElementAction())

          //插件处理
          afterElementAction(element)
        }

      }
      case None => {
        //当前页面已经遍历完成
        log.error("never access this")
      }
    }
    crawl()
  }


  /**
    * 如果pri=0 表示不用优先匹配. 只有遍历到了再寻找合适的action
    * @param element
    * @return
    */
  def getActionFromNormalActions(element: URIElement): String = {
    val normalActions = conf.triggerActions.filter(_.getOrElse("pri", 1).toString.toInt == 0)
    log.info(s"normal actions size = ${normalActions.size}")
    normalActions.toStream.map(r => {
      val xpath = r("xpath").toString
      val action = r("action").toString

      driver.getListFromXPath(xpath).toStream.filter(element==getUrlElementByMap(_)).headOption match {
        case Some(v)=>Some(action)
        case None => None
      }
    }).filter(_!=None).headOption match {
      case Some(action) => action.get
      case None => "click"
    }
  }


  def getTagLimitFromElementActions(element: URIElement): Option[Int] = {
    conf.tagLimit.foreach(r => {
      val xpath = r.getOrElse("xpath", "").toString
      val action = r.getOrElse("count", 1).toString.toInt

      val allMap = if (xpath.matches("/.*")) {
        //支持xpath
        driver.getListFromXPath(xpath).map(getUrlElementByMap(_))
      } else {
        //支持正则通配
        if (element.toTagPath().matches(xpath)) {
          List(element)
        } else {
          List[URIElement]()
        }
      }

      if (allMap.exists(_ == element)) {
        return Some(action)
      }
    })
    None
  }




  def saveLog(): Unit = {
    //记录点击log
    File(s"${conf.resultDir}/elements.yml").writeAll(DataObject.toYaml(store))
  }

  def getBasePathName(right:Int=1): String = {
    //序号_文件名
    val element=store.clickedElementsList.takeRight(right).head
    s"${conf.resultDir}/${store.clickedElementsList.size-right}_" + element.toFileName()
  }

  def saveDom(): Unit = {
    //保存dom结构
    val domPath = getBasePathName() + ".dom"
    //感谢QQ:434715737的反馈
    log.info(s"save to ${domPath}")
    Try(File(domPath).writeAll(driver.currentPageSource)) match {
      case Success(v) => {
        log.trace(s"save to ${domPath}")
      }
      case Failure(e) => {
        log.error(s"save to ${domPath} error")
        log.error(e.getMessage)
        log.error(e.getCause.toString)
        log.error(e.getStackTrace)
      }
    }
  }

  def saveScreen(force: Boolean = false): Unit = {
    //如果是schema相同. 界面基本不变. 那么就跳过截图加快速度.
    val originPath = getBasePathName() + ".clicked.png"
    if (pluginClasses.map(p => p.screenshot(originPath)).contains(true)) {
      return
    }
    if (conf.saveScreen || force) {
      Thread.sleep(100)
      log.info("start screenshot")
      driver.asyncTask(60) {
        val imgFile = if (store.isDiff()) {
          log.info("ui change screenshot again")
          driver.screenshot()
        } else {
          log.info("ui no change")
          val preImageFileName = getBasePathName(2) + ".clicked.png"
          val preImageFile = new java.io.File(preImageFileName)
          if (preImageFile.exists() && preImageFileName!=originPath) {
            log.info(s"copy from pre image file ${preImageFileName}")
            //FileUtils.copyFile(preImageFile, markImageFile)
            preImageFile
          } else {
            driver.screenshot()
          }
        }
        if(imgFile.getAbsolutePath==new java.io.File(originPath).getAbsolutePath){
          log.info(s"${imgFile.getAbsolutePath} same as before")
        }else{
          FileUtils.copyFile(imgFile, new java.io.File(originPath))
        }
      } match {
        case Some(v) => {
          log.info("screenshot success")
        }
        case None => {
          log.error("screenshot error")
        }
      }
    } else {
      log.info("skip screenshot")
    }
  }


  def doElementAction(element: URIElement, action: String): Unit = {
    //找到了要点击的元素或者其他的状态标记比如back swipe
    log.debug(s"push ${element}")
    store.setElementClicked(element)
    //todo: 如果有相同的控件被重复记录, 会出问题, 比如确定退出的规则

    log.info(s"current element = ${element}")
    log.info(s"current index = ${store.clickedElementsList.size - 1}")
    log.info(s"current action = ${action}")
    log.info(s"current url = ${element.url}")
    log.info(s"current xpath = ${element.loc}")
    log.info(s"current tag path = ${element.toTagPath()}")
    log.info(s"current file name = ${element.toFileName()}")
    log.info(s"current uri = ${element.toLoc()}")

    store.saveReqHash(contentHash.last().toString)
    store.saveReqImg(getBasePathName() + ".click.png")
    store.saveReqDom(driver.currentPageSource)

    val originImageName = getBasePathName(2) + ".clicked.png"
    val newImageName = getBasePathName() + ".click.png"

    action match {
      case "" => {
        log.info("just recored")
      }
      case "back" => {
        back()
      }
      case "backApp" => {
        if (conf.defaultBackAction.size > 0) {
          conf.defaultBackAction.foreach(Runtimes.eval)
        } else {
          driver.backApp()
        }
      }
      case "monkey" => {
        driver.event(element.name.toInt)
      }
      case crawl if crawl.contains("crawl\\(.*\\)") =>{
        store.clickedElementsList.remove(store.clickedElementsList.size-1)
        driver.dsl(crawl)
      }
      case code if code.matches(".*\\(.*\\).*") => {
        driver.dsl(code)
      }
      case str: String => {
        //todo: tap和click的行为不一致. 在ipad上有时候click会点错位置, 而tap不会
        //todo: tap的缺点就是点击元素的时候容易点击到元素上层的控件

        log.info(s"need input ${str}")
        driver.findElementByUrlElement(element) match {
          case true => {
            val rect = driver.getRect()
            if(conf.saveScreen) {
              log.info(s"mark ${originImageName} to ${newImageName}")
              driver.mark(originImageName, newImageName, rect.x, rect.y, rect.width, rect.height)
            }

            driver.asyncTask() {
              //支持各种动作
              str match {
                case "click" => {
                  log.info("click element")
                  driver.tap()
                }
                case "tap" => {
                  driver.longTap()
                }
                case str => {
                  log.info(s"input ${str}")
                  driver.sendKeys(str)
                }
              }
            }
          }
          case false => {
            log.warn(s"not found by ${element.loc}")
          }
        }
        if (List("UIATextField", "UIATextView", "EditText").map(element.tag.contains(_)).contains(true)) {
          driver.retry(driver.hideKeyboard())
        }
      }
    }

    val newImageFile=new io.File(newImageName)
    val originImageFile=new io.File(originImageName)
    if(newImageFile.exists()==false && originImageFile.exists()==true){
      log.info("use last clicked image replace mark")
      FileUtils.copyFile(originImageFile, newImageFile)
    }else{
      log.info("mark image exist")
    }

    isRefreshSuccess = refreshPage()
    saveDom()
    saveScreen()

    store.saveResHash(contentHash.last().toString)
    store.saveResImg(getBasePathName() + ".clicked.png")
    store.saveResDom(driver.currentPageSource)

  }

  def back(): Unit = {
    pluginClasses.foreach(_.beforeBack())
    if (conf.currentDriver.toLowerCase == "android") {
      if (backDistance.intervalMS() < 4000) {
        log.warn("two back action too close")
        Thread.sleep(4000)
      }
      driver.asyncTask() {
        driver.back()
      }
      backDistance.append("back")
      appNameRecord.pop()
    } else {
      log.warn("you should define you back button in the conf file")
    }
  }


  //通过规则实现操作. 不管元素是否被点击过
  def getElementByElementActions(): Option[URIElement] = {
    log.trace("rule match start")
    //先判断是否在期望的界面里. 提升速度
    conf.triggerActions.filter(_.getOrElse("pri", 1).toString.toInt == 1).foreach(r => {
      val xpath = r("xpath").toString
      val action = r.getOrElse("action", "click").toString
      val times = r.getOrElse("times", 0).toString.toInt
      log.debug(s"finding ${r}")

      driver.getListFromXPath(xpath).filter(isValid).headOption match {
        case Some(e) => {
          if (times == 1) {
            log.info(s"remove rule ${r}")
            conf.triggerActions -= r
          }
          r("times") = times - 1
          setElementAction(action)
          if (action == "monkey") {
            val count = conf.monkeyEvents.size
            val random = util.Random.nextInt(count)
            val code = conf.monkeyEvents(random)
            return Some(URIElement("Monkey", "", "", s"${code}", s"event-${code}"))
          } else {
            return Some(getUrlElementByMap(e))
          }
        }
        case None => {}
      }
    })
    None
  }

  def stop(): Unit = {
    stopAll = true
    if (signalInt < 2) {
      Try(pluginClasses.foreach(_.stop())) match {
        case Success(v)=> {}
        case Failure(e) => {
          log.error(e.getMessage)
          log.error(e.getCause)
          e.getStackTrace.foreach(log.error)
        }
      }
      log.info("generate report finish")
      sys.exit()
    }
  }

  def handleCtrlC(): Unit = {
    log.info("add shutdown hook")
    Signal.handle(new Signal("INT"), new SignalHandler() {
      def handle(sig: Signal) {
        log.info("exit by INT")
        signalInt += 1
        stop()
        sys.exit(1)
      }
    })
  }
}
