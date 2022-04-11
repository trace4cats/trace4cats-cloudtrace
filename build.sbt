lazy val commonSettings = Seq(
  Compile / compile / javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
  libraryDependencies ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq(compilerPlugin(Dependencies.kindProjector), compilerPlugin(Dependencies.betterMonadicFor))
      case _ => Seq.empty
    }
  },
  scalacOptions += {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) => "-Wconf:any:wv"
      case _ => "-Wconf:any:v"
    }
  },
  Test / fork := true,
  resolvers += Resolver.sonatypeRepo("releases"),
)

lazy val noPublishSettings =
  commonSettings ++ Seq(publish := {}, publishArtifact := false, publishTo := None, publish / skip := true)

lazy val publishSettings = commonSettings ++ Seq(
  publishMavenStyle := true,
  pomIncludeRepository := { _ =>
    false
  },
  Test / publishArtifact := false
)

lazy val root = (project in file("."))
  .settings(noPublishSettings)
  .settings(name := "Trace4Cats Cloud Trace")
  .aggregate(`stackdriver-common`, `stackdriver-grpc-exporter`, `stackdriver-http-exporter`)

lazy val `stackdriver-common` =
  (project in file("modules/stackdriver-common"))
    .settings(publishSettings)
    .settings(name := "trace4cats-stackdriver-common")

lazy val `stackdriver-grpc-exporter` =
  (project in file("modules/stackdriver-grpc-exporter"))
    .settings(publishSettings)
    .settings(
      name := "trace4cats-stackdriver-grpc-exporter",
      libraryDependencies ++= Seq(
        Dependencies.collectionCompat,
        Dependencies.googleCredentials,
        Dependencies.googleCloudTrace,
        Dependencies.trace4catsModel,
        Dependencies.trace4catsKernel,
        Dependencies.trace4catsExporterCommon
      )
    )
    .dependsOn(`stackdriver-common`)

lazy val `stackdriver-http-exporter` =
  (project in file("modules/stackdriver-http-exporter"))
    .settings(publishSettings)
    .settings(
      name := "trace4cats-stackdriver-http-exporter",
      libraryDependencies ++= Seq(
        Dependencies.circeGeneric,
        Dependencies.circeParser,
        Dependencies.http4sCirce,
        Dependencies.jwt,
        Dependencies.log4cats,
        Dependencies.trace4catsModel,
        Dependencies.trace4catsKernel,
        Dependencies.trace4catsExporterCommon,
        Dependencies.trace4catsExporterHttp
      ),
      libraryDependencies ++= Seq(Dependencies.http4sBlazeClient, Dependencies.trace4catsTestkit).map(_ % Test)
    )
    .dependsOn(`stackdriver-common`)
