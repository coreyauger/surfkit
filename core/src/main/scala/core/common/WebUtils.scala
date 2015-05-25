package io.surfkit.utils

import java.net.URL
import java.util.regex.Pattern
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec


import com.ning.http.client.providers.netty.NettyResponse
import org.apache.commons.codec.binary.Base64
import org.apache.commons.lang3.StringEscapeUtils
import org.w3c.dom.Document
import play.api.Play
import play.api.Play.current
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.WS

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Created by suroot on 5/25/14.
 */
object WebUtils {


  private def encode(keyString: String, data: String, alg:String) ={
    val key = new SecretKeySpec((keyString).getBytes("UTF-8"), alg);
    val mac = Mac.getInstance(alg);
    mac.init(key);

    val bytes = mac.doFinal(data.getBytes("UTF-8"));
    val encoder = new Base64()
    new String( encoder.encode(bytes) )
  }

  def HmacSHA256(keyString: String, data: String) = {
    encode(keyString,data,"HmacSHA256")
  }

  def HmacSHA1(keyString: String, data: String) = {
    encode(keyString,data,"HmacSHA1")
  }

  /*
  def webGet(url:String, path:String) = {
    WS.url(url).get().map { r =>
      val output:Output = Resource.fromOutputStream(new java.io.FileOutputStream(path))

      // Note: each write will open a new connection to file and
      //       each write is executed at the begining of the file,
      //       so in this case the last write will be the contents of the file.
      // See Seekable for append and patching files
      // Also See openOutput for performing several writes with a single connection
      output.write(r.body)
      //output.writeIntsAsBytes(1,2,3)
      //output.write("hello")(Codec.UTF8)
      //output.writeStrings(List("hello","world")," ")(Codec.UTF8)
    }
  }
  */


  def extractUrls(input: String ):List[String] = {
    var ret = List[String]()
    val pattern = Pattern.compile("\\b(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]",Pattern.CASE_INSENSITIVE);
    val matcher = pattern.matcher(input);
    while (matcher.find()) {
      //matcher.group() :: ret
      ret = matcher.group() :: ret
    }
    ret
  }

  /*
  val mauiMaxDataSize = Play.current.configuration.getBytes("maui.maxdatasize").getOrElse((512000).toLong)

  def urlToLinkCardJson(urlStr:String):Future[JsValue] = {
    val url = new URL(urlStr)
    WS.url(url.toString).withFollowRedirects(true).get.map {
      req =>
        val actualUrl = req.underlying[NettyResponse].getUri
        println(s"ACUTUAL URL: $actualUrl")

        try {
          val cleanerProperties = new CleanerProperties()
          // TODO does not seem to help for filtering binary input =(
          //cleanerProperties.setAdvancedXmlEscape(true)
          //cleanerProperties.setOmitXmlDeclaration(false)
          //cleanerProperties.setOmitDoctypeDeclaration(false)
          //cleanerProperties.setRecognizeUnicodeChars(true)
          //cleanerProperties.setTranslateSpecialEntities(false)
          //cleanerProperties.setOmitUnknownTags(true)
          //cleanerProperties.setIgnoreQuestAndExclam(true)
          //cleanerProperties.setUseEmptyElementTags(false)
          cleanerProperties.setNamespacesAware(false)
          val cleaner = new HtmlCleaner(cleanerProperties)
          val node = cleaner.clean(req.body)
          // The following line validates the supposed to be "HTML" (raises an exception if not HTML)
          val doc: Document = new DomSerializer(cleanerProperties).createDOM(node)

          val serializer = new PrettyHtmlSerializer(cleanerProperties)
          if(serializer.getAsString(node).getBytes("UTF-8").length > mauiMaxDataSize) {
            throw new IllegalArgumentException();
          }

          val titleNode = node.getElementsByName("title",true)
          val title =
            if( titleNode.length > 0){
              titleNode(0).getText
            }else{
              ""  // emtpy title in an html doc ??
            }
          val imgNodes = node.getElementsByName("img",true)
          // we choose 4 random images for now.
          val meta:Map[String, String] = node.getElementsByName("meta",true).flatMap{
            m =>
              val list = m.getAttributes.toMap.map{
                inner =>
                  inner._2
              }.toList
              val a = list.zip(list.reverse).take(list.length/2)
              a
          }.toMap

          println("MAUI MAUI MAUI MAUI MAUI ...")

          //val bodyNode = node.getElementsByName("p",true)
          println("... calling extract.")
          val paraNode = node.getElementsByName("p",true).map(_.getText).mkString("")
          //val liNode = node.getElementsByName("li",true).map(_.getText).mkString("")
          val content = title + meta.get("description").getOrElse("") + paraNode //+ liNode
          //println(bodyNode.map( n => n.getText.toString ).flatten.mkString(""))
          // (CA) - the idea here is we filter out any text nodes that don't contain a "period" ie not a sentence.
          //val content = bodyNode. filter(_.getText.toString.indexOf('.') > 0 ).map( n => n.getText.toString ).flatten.mkString("")
          //println(content)
          val realTopics = WalkaboutAPI.extractTopicsFromText(content).toList

          println("META")
          println(meta)

          val toDrop = imgNodes.length/4
          val toTake = Math.min(3, imgNodes.length-toDrop)
          val imgList =
            StringEscapeUtils.unescapeHtml4(meta.getOrElse("og:image","")) ::
              (if( req.header("Content-Type").getOrElse("").contains("image") )
                actualUrl.toString
              else
                "") ::
            imgNodes.drop(toDrop).take(toTake).map{
              i =>
                val s = i.getAttributeByName("src")
                val src = if(s != null && s.contains("://"))
                  s
                else
                  "%s://%s/%s".format(actualUrl.getScheme,actualUrl.getHost,s)
                src
            }.toList

          Json.obj(
            "id" -> "link",
            "title" -> StringEscapeUtils.unescapeHtml4(title.toString),
            "description" -> Json.toJson(StringEscapeUtils.unescapeHtml4(meta.getOrElse("og:description",""))),
            "link" -> actualUrl.toString,
            "button" -> Json.obj(
              "title" -> "related content"
            ),
            "content" -> content,
            "topics" -> Json.toJson(realTopics),
            "imgs" -> imgList.filterNot(_==""),
            "meta" -> Json.toJson(meta)
          )
      } catch {
        case e:Exception =>
          val message = e.getMessage()
          Json.obj(
            "id" -> "link",
            "title" -> actualUrl.toString,
            "description" -> "",
            "link" -> actualUrl.toString,
            "button" -> Json.obj(
              "title" -> "related content"
            ),
            "content" -> Json.obj(),
            "topics" -> Json.arr(),
            "imgs" -> Json.arr(),
            "meta" -> Json.obj()
          )
      }
    }

  }
*/
}
