name := "coinmines"

version := "0.1"

organization := "ag.bett.lift"

scalaVersion := "2.9.0-1"

jettyScanDirs := Nil

seq(WebPlugin.webSettings: _*)

resolvers := Seq(
	MavenRepository("Evil-Packet", "http://evil-packet.org/m2"),
	MavenRepository("JBoss", "https://repository.jboss.org/nexus/content/repositories/scala-tools-releases"))

libraryDependencies ++= Seq(
	"net.liftweb" %% "lift-webkit" % "2.4-M2" % "compile->default",
	"net.liftweb" %% "lift-mapper" % "2.4-M2" % "compile->default",
	"net.liftweb" %% "lift-widgets" % "2.4-M2" % "compile->default",
	"postgresql" % "postgresql" % "9.0-801.jdbc4")

libraryDependencies ++= Seq(
	"com.twitter" % "json" % "2.1.4",
	"redis.clients" % "jedis" % "2.0.0",
	"net.databinder" %% "dispatch-http" % "0.8.5",
	"ag.bett.scala" % "scala-libs" % "1.0",
	"com.github.scala-incubator.io" %% "core" % "0.1.2",
	"com.github.scala-incubator.io" %% "file" % "0.1.2",
	"org.mortbay.jetty" % "jetty" % "6.1.22" % "jetty",
	"junit" % "junit" % "4.5" % "test",
	"ch.qos.logback" % "logback-classic" % "0.9.26",
	"org.scala-tools.testing" %% "specs" % "1.6.8" % "test")
