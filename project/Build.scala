
import sbt._
import sbt.Keys._
import com.typesafe.sbt.packager.linux.{LinuxPackageMapping, LinuxSymlink}
import com.typesafe.sbt.packager.rpm.RpmDependencies

import com.typesafe.sbt.packager.Keys._
import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.sbt.packager.archetypes.TemplateWriter

///import com.typesafe.sbt.packager.universal.Keys.stagingDirectory

import play.sbt._
import Play.autoImport._
import PlayKeys._
import play.sbt.routes.RoutesKeys._

import com.typesafe.sbt.web._
import com.typesafe.sbt.web.SbtWeb._
import com.typesafe.sbt.web.Import._
import com.typesafe.sbt.web.Import.WebKeys._



object ApplicationBuild extends Build {

  val appVersion = "2.6.7-SNAPSHOT"

  ///val hiveVersion = "0.14.0.2.2.6.5-3"
  val hiveVersion = "1.0.0"

  ///val hiveMetastoreVersion = "1.2.0"
  val hiveMetastoreVersion = "1.0.0"
  ///val hiveMetastoreVersion = "0.14.0.2.2.6.5-3"

  ///val hadoopVersion = "2.6.0.2.2.6.5-3"
  val hadoopVersion = "2.7.2"

  val core = Project(
      "satisfaction-core",
      file("modules/core")
  ).settings(CommonSettings: _* ).settings(libraryDependencies := coreDependencies ++ testDependencies )

  val engine = Project(
      "satisfaction-engine",
      file("modules/engine")
  ).settings(CommonSettings: _*).settings( libraryDependencies := engineDependencies ).dependsOn(core)

  val hadoop = Project(
      "satisfaction-hadoop",
      file("modules/hadoop")
  ////).settings(CommonSettings: _*).settings(libraryDependencies := hadoopDependencies ).dependsOn(core).dependsOn( engine )
  ).settings(CommonSettings: _*).settings(libraryDependencies := hadoopDependencies ).dependsOn(core)

  val metastore = Project(
      "satisfaction-hive-ms",
      file("modules/hive-ms")
  ).settings(CommonSettings: _*).settings(libraryDependencies := metastoreDependencies ).dependsOn(core).dependsOn(hadoop)

  val hive = Project(
      "satisfaction-hive",
      file("modules/hive")
  ).settings(CommonSettings: _*).settings(libraryDependencies  := hiveDependencies ).dependsOn(core).dependsOn(hadoop).dependsOn(metastore)

  val willrogers = Project(
      "willrogers",
      file("apps/willrogers")
  ).enablePlugins(PlayScala, SbtWeb)
   .dependsOn(core)
   .aggregate(core)
   .dependsOn(engine)
   .aggregate(engine)
   .settings( 
     version := appVersion,

     routesGenerator := StaticRoutesGenerator,

     ////sbt-web doesn't automatically include the assets 

     (unmanagedResourceDirectories in Compile) += (webTarget in Assets).value,

     (packageBin in Compile) <<= (packageBin in Compile).dependsOn(assets in Assets),

     (packageBin in Rpm) <<= (packageBin in Rpm).dependsOn(packageBin in Compile),

     libraryDependencies ++= Seq(
	  ("com.stitchfix.algorithms" %% "satisfaction-core" % appVersion),
	  ("com.stitchfix.algorithms" %% "satisfaction-engine" % appVersion)
     )

   ).settings(CommonSettings: _*).settings( AppSettings: _* ).settings(RpmSettings: _* )

  def CommonSettings =  Resolvers ++ Seq( 
     javacOptions in Compile ++= Seq("-source", "1.8", "-target", "1.8"),

      scalacOptions ++= Seq(
          "-unchecked",
          "-feature",
          "-deprecation",
          "-language:existentials",
          "-language:postfixOps",
          "-language:implicitConversions",
          "-language:reflectiveCalls"
      ),

      ///scalaVersion := "2.10.2",
      scalaVersion := "2.11.8",

      organization := "com.stitchfix.algorithms",

      version := appVersion,

      packageSummary := "sticky_fingers",

      libraryDependencies ++= testDependencies,

      ///fork := true,

      publishMavenStyle := true,

      publishTo := Some("Snapshots" at "http://artifactory.vertigo.stitchfix.com/artifactory/snapshots"),

      isSnapshot := true,

      credentials += Credentials("Artifactory Realm", "artifactory.vertigo.stitchfix.com", "admin", "password")
  ) 

  def AppSettings =  CommonSettings ++ Seq(

     unmanagedResourceDirectories in Assets += baseDirectory.value / "public",

     libraryDependencies ++= awsDependencies
  )


  def resolveRpmVersion() = {
    sys.env.get("BUILD_NUMBER") match {
      case Some(buildNumber) => buildNumber
      case None => "2.6.8"
    }
  }

  ////def RpmSettings = packagerSettings ++ deploymentSettings ++ packageArchetype.java_server ++  Seq(
  def RpmSettings =   packageArchetype.java_server ++  Seq(
    maintainer in Linux := "Jerome Banks jbanks@tagged.com",
    packageSummary in Linux := "Satisfaction",
    packageDescription in Linux := "The Next Generation Hadoop Scheduler",
    daemonUser in Linux := "satisfaction",
    daemonGroup in Linux := "satisfaction",
    normalizedName in Linux := "satisfaction",


    ///linuxPackageMappings <++= (mappings in Universal) map { universalDir => 
        ///universalDir.filter( {  _._2.toString.startsWith("/usr/share") } ).map { packageMapping( _ ) } 
    ////},


    mappings in Universal <+= (packageBin in Compile, baseDirectory ) map { (_, base) =>
       val conf = base / "conf" / "application.conf"
       conf -> "conf/application.conf"
    },

    mappings in Universal <+= (packageBin in Compile, baseDirectory ) map { (_, base) =>
       val conf = base / "conf" / "logger.xml"
       conf -> "conf/logger.xml"
    },


    name in Rpm := "satisfaction-scheduler",
    ///version in Rpm := appVersion,
    version in Rpm := resolveRpmVersion(),

    ////rpmRelease in Rpm:= resolveRpmVersion(),
    rpmRelease in Rpm := "2",
    rpmBrpJavaRepackJars := true,
    packageSummary in Rpm := "wyman",
    packageSummary in Linux := "wyman",
    rpmVendor in Rpm := "Tagged.com",
    rpmUrl in Rpm:= Some("http:/github.com/ifwe/satisfaction"),
    rpmLicense in Rpm:= Some("Apache License Version 2"),
    packageDescription in Rpm := "Next Generation Hadoop Scheduler",
    rpmGroup in Rpm:= Some("satisfaction"),

    rpmPreun := Option("""
if [[ $1 == 0 ]] ; then
    echo "Shutdown willrogers"
    service willrogers stop || echo "Could not stop willrogers"
fi"""),

    rpmPost := Option("""
export JAVA_HOME=/usr/java/default
export HADOOP_CONF_DIR=/usr/hdp/current/hadoop-client/etc/hadoop
export HADOOP_HOME=/usr/hdp/current/hadoop-client 
export HIVE_CONF_DIR=/usr/hdp/current/hive-client/conf
""")


  )

  def excludeFromAll(items: Seq[ModuleID], group: String, artifact: String) = 
    items.map(_.exclude(group, artifact))

  implicit def dependencyFilterer(deps: Seq[ModuleID]) = new Object {
		    def excluding(group: String, artifactId: String) =
			    deps.map(_.exclude(group, artifactId))

		    def excludingGroup(group: String) =
			    deps.map(_.exclude(group, "*"))
  }


 
  def testDependencies = Seq(
    ("junit" % "junit" % "4.10" % "test" intransitive() ),
    ("org.specs2" %% "specs2" % "3.3.1" % "test"  ),
    ("org.specs2" %% "specs2-junit" % "3.3.1" % "test"  )
  )


  def hadoopDependencies = Seq(
	  ("org.apache.hadoop" % "hadoop-common" % hadoopVersion),
	  ("org.apache.hadoop" % "hadoop-hdfs" % hadoopVersion),
	  ("org.apache.hadoop" % "hadoop-mapreduce-client-app" % hadoopVersion),
	  ("org.apache.hadoop" % "hadoop-mapreduce-client-common" % hadoopVersion),
	  ("org.apache.hadoop" % "hadoop-mapreduce-client-core" % hadoopVersion),
	  ("org.apache.hadoop" % "hadoop-mapreduce-client-jobclient" % hadoopVersion),
	  ("org.apache.hadoop" % "hadoop-distcp" % hadoopVersion),
	  ("org.hamcrest" % "hamcrest-core" % "1.3"  ) ,
          ("ch.qos.logback" % "logback-classic" % "1.0.13" ),
          ("org.slf4j" % "log4j-over-slf4j" % "1.7.7" )
  ).excluding("commons-daemon", "commons-daemon" )
	.excluding("junit","junit")
	.excluding("log4j", "log4j")
        .excluding("org.slf4j","slf4j-log4j12")
        .excluding("org.mortbay.jetty","jetty")
        .excluding("org.mortbay.jetty","jetty-util")
        .excluding("org.jboss.netty", "netty" ) ++ testDependencies 

  def coreDependencies = Seq(
    ("org.slf4j" % "slf4j-api" % "1.7.7"),
    ("com.github.nscala-time" %% "nscala-time" % "1.2.0"),
    ("org.scala-lang" % "scala-library" % "2.11.7" ),
    ("org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.2"),
    ("org.apache.commons" % "commons-email" % "1.3.3" )
  ) ++ testDependencies 

  def jsonDependencies = Seq(
   ("org.json4s" %% "json4s-jackson" % "3.2.9" )
 )

  def metastoreDependencies = Seq(
	  ("org.apache.hive" % "hive-common" % hiveVersion),
	  ("org.apache.hive" % "hive-shims" % hiveVersion),
	  ("org.apache.hive" % "hive-metastore" % hiveMetastoreVersion),
	  ("org.apache.hive" % "hive-serde" % hiveVersion),
	  ("org.apache.hive" % "hive-exec" % hiveVersion),
	  ("org.apache.calcite" % "calcite-core" % "0.9.1-incubating"),
	  ("org.apache.calcite" % "calcite-avatica" % "0.9.1-incubating"),
	  ("org.apache.thrift" % "libfb303" % "0.7.0")
  ).excluding( "log4j", "log4j" ).excluding("org.slf4j", "slf4j-log4j12")
   .excluding("org.mortbay.jetty", "jetty")
   .excluding("org.mortbay.jetty", "jetty-util")
   .excluding("org.jboss.netty", "netty")
   .excluding("org.pentaho", "pentaho-aggdesigner-algorithm")

  def hiveDependencies = Seq(
	  ("org.apache.hive" % "hive-common" % hiveVersion),
	  ("org.apache.hive" % "hive-exec" % hiveVersion),
	  ("org.apache.hive" % "hive-metastore" % hiveMetastoreVersion),
	  ("org.apache.hive" % "hive-service" % hiveVersion),
	  ("org.apache.hive" % "hive-serde" % hiveVersion),
	  ("org.apache.hive" % "hive-shims" %   hiveVersion ),
	  ("org.apache.hive" % "hive-hbase-handler" % hiveVersion),
	  ("org.apache.hive" % "hive-jdbc" % hiveVersion),
	  ("org.apache.hive" % "hive-service" % hiveVersion ),
	  ////("org.apache.calcite" % "calcite-core" % "0.9.1-incubating"),
	  /////("org.apache.calcite" % "calcite-avatica" % "0.9.1-incubating"),
	  ("org.apache.thrift" % "libfb303" % "0.7.0" ),
	  ("org.antlr" % "antlr-runtime" % "3.4" )
  ).excluding("org.slf4j", "slf4j-log4j12")
   .excluding("org.jboss.netty", "netty") 
   .excluding("org.mortbay.jetty", "jetty")
   .excluding("org.mortbay.jetty", "jetty-util") ++ metastoreDependencies ++ testDependencies


  def engineDependencies = Seq(
    ("com.typesafe.akka" %% "akka-actor" % "2.3.9"),
    ("org.quartz-scheduler" % "quartz" % "2.2.1"),
    ("ch.qos.logback" % "logback-classic" % "1.0.13" ),
    ("com.typesafe.slick" %% "slick" % "3.1.1"),
    ("com.h2database" % "h2" % "1.3.170"),
    ("com.typesafe.slick" %% "slick" % "2.0.2"),
    ////("nl.grons" %% "metrics-scala" % "3.3.0_a2.2"),
    ("ch.qos.logback" % "logback-classic" % "1.0.13" )
  ) ++ testDependencies ++ jsonDependencies


  /**
   *  For AWS Version of WillRogers, 
   *    Depend upon the 
   *  satisfaction-s3 package instead of Hadoop
   */
  def awsDependencies = Seq(
    ("com.stitchfix.algorithms" %% "satisfaction-s3" % "0.0.4"),
    ("com.stitchfix.algorithms" %% "satisfaction-core" % appVersion),
    ("com.stitchfix.algorithms" %% "satisfaction-engine" % appVersion)
  )



  def Resolvers = resolvers ++= Seq(
      "HortonWorks Releases" at "http://repo.hortonworks.com/content/repositories/releases/",
      "ConJars.org" at "http://conjars.org/repo",
      "SF snapshots" at "http://artifactory.vertigo.stitchfix.com/artifactory/snapshots",
      "SF releases"  at "http://artifactory.vertigo.stitchfix.com/artifactory/releases",
      "snapshots" at "http://oss.sonatype.org/content/repositories/snapshots",
      "releases"  at "http://oss.sonatype.org/content/repositories/releases",
      "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
      "Maven Central" at "http://repo1.maven.org/maven2",
      "Apache Maven Repository" at "http://people.apache.org/repo/m2-snapshot-repository/",
      "ScalaToolsMaven2Repository" at "http://scala-tools.org/repo-releases",
      "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository"
  )

}



