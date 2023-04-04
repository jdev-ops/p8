//> using scala "3.2.2"
//> using lib "org.scala-lang::scala3-compiler:3.2.2"
//> using lib "org.apache.commons:commons-configuration2:2.9.0"
//> using lib "com.github.jknack:handlebars:4.3.1"

import com.github.jknack.handlebars.Handlebars
import org.apache.commons.configuration2.INIConfiguration

import java.io.{File, FileReader, FileWriter}
import java.nio.ByteBuffer
import java.nio.charset.{CharacterCodingException, Charset}
import java.util
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.io.StdIn.readLine
import java.nio.file.{Files, Paths}

import math.Fractional.Implicits.infixFractionalOps
import math.Integral.Implicits.infixIntegralOps
import math.Numeric.Implicits.infixNumericOps

def mkString(args: String*): String =
  args.mkString("")

def isValidUTF8(bytes: Array[Byte]): Boolean =
    try
        Charset.availableCharsets().get("UTF-8").newDecoder().decode(ByteBuffer.wrap(bytes));
        true
    catch
      case e: CharacterCodingException =>
        false

extension[T] (b: mutable.ListBuffer[T])
  def remove(filter: T => Boolean): Unit =
    b.filterInPlace(!filter(_))

extension (b: File)
  def startsWithAny(strs: String*): Boolean =
    strs.exists(b.getName.startsWith(_))

def camel_to_snake(str: String): String =
  // Regular Expression
  val regex = "([a-z])([A-Z]+)"
  // Replacement string
  val replacement = "$1_$2";
  // Replace the given regex
  // with replacement string
  // and convert it to lower case.
  str.replaceAll(regex, replacement).toLowerCase

var data: mutable.HashMap[String, String] = mutable.HashMap[String, String]()

def readDataFromINI(path: String): mutable.HashMap[String, String] =
  val iniFileContents = new mutable.HashMap[String, mutable.HashMap[String, String]]
  val fileToParse = new File(path)
  val iniConfiguration = new INIConfiguration()
  val fileReader = new FileReader(fileToParse)
  iniConfiguration.read(fileReader)
  for section <- iniConfiguration.getSections.asScala do
    val subSectionMap = new mutable.HashMap[String, String]
    val confSection = iniConfiguration.getSection(section)
    val keyIterator = confSection.getKeys()
    while keyIterator.hasNext do
      val key = keyIterator.next()
      val value = confSection.getProperty(key).toString
      subSectionMap.put(key, value)
    iniFileContents.put(section, subSectionMap)
  iniFileContents("DEFAULT")

object os:
  def walk(start: File, body: (String, mutable.ListBuffer[File], Seq[File]) => Unit): Unit =
    val (allD, allFiles) = start.listFiles.toList.partition(_.isDirectory)
    val allDirs = mutable.ListBuffer[File]() addAll allD
    body(start.getAbsolutePath, allDirs, allFiles)
    for d <- allDirs do walk(d, body)

@main
def main(): Unit =
  val path = System.getenv("TEMPLATE_PATH")
  val destination = System.getenv("DESTINATION_PATH")
  val config_path = f"${path}/.default.ini"
  data ++= readDataFromINI(config_path)
//  File(config_path).delete()
  val normal = for (k, v) <- data if !v.startsWith("$") yield (k, v)

  val expressions = for (k, v) <- data if v.startsWith("$") yield (k, v.substring(1))
  val m = new javax.script.ScriptEngineManager(getClass.getClassLoader)
  val e = m.getEngineByName("scala")

  def createDynamicEnv(): Unit =
    for (k, v) <- normal do
      e.eval(f"var ${k} = \"${v}\"")

    for (k, v) <- expressions do
      e.eval(f"var ${k} = \"${v}\"")

  def createOrUpdateDynamicEnv(): Unit =
    for (k, v) <- normal do
//      e.eval(f"var ${k} = \"${v}\"")
      e.eval(f"data(\"${k}\") = ${k}")

    for (k, v) <- expressions do
//      e.eval(f"var ${k} = \"${v}\"")
      e.eval(f"data(\"${k}\") = ${v}")


  def updateExpressions(): Unit =
    for (k, v) <- expressions do
      e.eval(f"data(\"${k}\") = ${v}")

  def readKeyValues(d: mutable.Map[String, String]): Unit =
    for (k, _) <- d do
      e.eval(
        f"""
           |  print(\"What ${k}? [${data(k)}]: \")
           |  var ${k}_ = scala.io.StdIn.readLine()
           |  if (${k}_ != ""){
           |    data(\"${k}\") = ${k}_
           |    ${k} = ${k}_
           |  }
           | """.stripMargin
      )

  createDynamicEnv()
  createOrUpdateDynamicEnv()
  readKeyValues(normal)
  updateExpressions()

  val jdata = data.asJava
  val handlebars = Handlebars()

  def readFile(f: File): String =
    val bufferedSource = scala.io.Source.fromFile(f)
    val str = bufferedSource.getLines().mkString("\n")
    bufferedSource.close()
    str

  def writeToFile(fileDir: String, content: String): Unit =
    val fileWriter = new FileWriter(new File(fileDir))
    fileWriter.write(content)
    fileWriter.close()

  os.walk(new File(path), (root, dirs, files) => {
    dirs.remove(_.startsWithAny(".git", ".idea"))
    val nroot = root.substring(path.length)
    var ndir = f"${destination}/$nroot"
    var template = handlebars.compileInline(ndir)
    ndir = template.apply(jdata)
    if !File(ndir).isDirectory then
      Files.createDirectories(Paths.get(ndir))

    for f <- files if !(f.getName == ".default.ini") do
      val allBytes = Files.readAllBytes(Paths.get(f.getCanonicalPath))
      if isValidUTF8(allBytes) then
        val str = readFile(f)
        template = handlebars.compileInline(str)
        val templateFileName = handlebars.compileInline(f.getName)
        val ff = templateFileName.apply(jdata)
        writeToFile(f"${ndir}/${ff}", template.apply(jdata))
      else
        val templateFileName = handlebars.compileInline(f.getName)
        val ff = templateFileName.apply(jdata)
        Files.write(Paths.get(f"${ndir}/${ff}"), allBytes)

  })

