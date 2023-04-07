//> using scala "3.2.2"
//> using lib "org.scala-lang::scala3-compiler:3.2.2"
//> using lib "org.apache.commons:commons-configuration2:2.9.0"
//> using lib "com.hubspot.jinjava:jinjava:2.7.0"
//> using lib "info.picocli:picocli:4.7.1"

// -- > using lib "com.github.scopt::scopt:4.1.0"

import com.hubspot.jinjava.Jinjava
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

def convertToListWhenApply(v: String): util.List[String] | String =
  if v.startsWith("#") then
    v.substring(1).split('|').map(_.strip).toList.asJava
  else
    v

def camelToSnake(str: String): String =
  // Regular Expression
  val regex = "([a-z])([A-Z]+)"
  // Replacement string
  val replacement = "$1_$2";
  // Replace the given regex
  // with replacement string
  // and convert it to lower case.
  str.replaceAll(regex, replacement).toLowerCase

def readDataFromINI(path: String, segment: String = "DEFAULT"): mutable.HashMap[String, String] =
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
  iniFileContents(segment)

object os:
  def walk(start: File, body: (String, mutable.ListBuffer[File], Seq[File]) => Unit): Unit =
    val (allD, allFiles) = start.listFiles.toList.partition(_.isDirectory)
    val allDirs = mutable.ListBuffer[File]() addAll allD
    body(start.getAbsolutePath, allDirs, allFiles)
    for d <- allDirs do walk(d, body)

def readFile(f: File): String =
  val bufferedSource = scala.io.Source.fromFile(f)
  val str = bufferedSource.getLines().mkString("\n")
  bufferedSource.close()
  str

def writeToFile(fileDir: String, content: String): Unit =
  val fileWriter = new FileWriter(new File(fileDir))
  fileWriter.write(content)
  fileWriter.close()

//@main
def mainGenerator(): Unit =
  val configurationFileName: String = Option(System.getenv("CONFIGURATION_FILE_NAME")).getOrElse(".default.ini")
  val selectorFileName: String = Option(System.getenv("SELECTOR_FILE_NAME")).getOrElse(".selector.ini")
  val prefix: String = Option(System.getenv("PARAMETERS_PREFIX")).getOrElse("P8_PARAM_")
  val path: String = System.getenv("TEMPLATE_PATH")
  if path == null then
    println("TEMPLATE_PATH is not set")
    System.exit(1)
  val destination: String = System.getenv("DESTINATION_PATH")
  if destination == null then
    println("DESTINATION_PATH is not set")
    System.exit(1)
  val config_path = Paths.get(path, configurationFileName).toString
  val sData = readDataFromINI(config_path, "DOMAIN")
  val support_data: mutable.Map[String, String | util.List[String]] = for ((k, v) <- sData) yield (k, convertToListWhenApply(v))
  val d = System.getenv()
  val data: mutable.Map[String, util.List[String] | String] = for ((k, v) <- d.asScala if k.startsWith(prefix)) yield (k.substring(prefix.length), convertToListWhenApply(v))

  val jdata = data.asJava
  val jinjava = new Jinjava();

  os.walk(new File(path), (root, dirs, files) => {
    dirs.remove(_.startsWithAny(".git", ".idea"))
    val nroot = root.substring(path.length)
    var template = Paths.get(destination, nroot).toString
    val ndir = jinjava.render(template, jdata)
    if !File(ndir).isDirectory then
      Files.createDirectories(Paths.get(ndir))
    for f <- files if f.getName == selectorFileName do
      val selectorDataCurrentDir = readDataFromINI(f.getAbsolutePath, "DEFAULT")
      val dirActives = data(selectorDataCurrentDir("value")).asInstanceOf[util.List[String]].asScala.toSet
      val allDir = support_data(selectorDataCurrentDir("value")).asInstanceOf[util.List[String]].asScala.toSet
      for delDir <- allDir -- dirActives do
        dirs.remove(_.getName == delDir)

    for f <- files if f.getName != configurationFileName && f.getName != selectorFileName do
      val allBytes = Files.readAllBytes(Paths.get(f.getCanonicalPath))
      if isValidUTF8(allBytes) then
        template = readFile(f)
        val filePath = jinjava.render(f.getName, jdata)
        val fToWrite = Paths.get(ndir, filePath)
        writeToFile(fToWrite.toString, jinjava.render(template, jdata))
      else
        val filePath = jinjava.render(f.getName, jdata)
        Files.write(Paths.get(ndir, filePath), allBytes)

  })


import java.util.concurrent.Callable
import picocli.CommandLine
import picocli.CommandLine.{Command, HelpCommand, Parameters}

@Command(name = "p8", version = Array("p8 v0.1"),
  description = Array("@|bold Generator"))
class CLIApp extends Runnable {

  @CommandLine.Option(arity = "2..3", names = Array("-g", "--get"), paramLabel = "GET", description = Array("Get INI values"))
  private var scmds: Array[String] = Array()

  @CommandLine.Option(names = Array("-h", "--help"), usageHelp = true, description = Array("print this help and exit"))
  private var helpRequested = false

  @CommandLine.Option(names = Array("-V", "--version"), versionHelp = true, description = Array("print version info and exit"))
  private var versionRequested = false

  def run(): Unit = {
    if (helpRequested) new CommandLine(this).usage(System.err) else
      if (versionRequested) new CommandLine(this).printVersionHelp(System.err) else
        if (scmds.length > 0){
          scmds.length match {
            case 2 =>
              val data = readDataFromINI(scmds(0), scmds(1))
              for ((k, _) <- data) println(k)
            case 3 =>
              val data = readDataFromINI(scmds(0), scmds(1))
              println(data(scmds(2)))
            case _ => println("Wrong number of arguments")
          }
        }
        else
          mainGenerator()
  }
}


@main
def main(args: String*): Unit =
  System.exit(new CommandLine(new CLIApp()).execute(args: _*))