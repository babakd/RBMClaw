import java.io.File

pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}

fun readGithubTokenFromEnvFiles(root: File): String {
  val candidates = listOf(
    File(root, ".env"),
    File(root.parentFile ?: root, ".env"),
  )
  for (file in candidates) {
    if (!file.exists()) continue
    val token =
      file.readLines()
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
        .map { line ->
          val idx = line.indexOf("=")
          val key = line.substring(0, idx).trim()
          val value = line.substring(idx + 1).trim().trim('"', '\'')
          key to value
        }
        .firstOrNull { (key, value) ->
          (key == "GITHUB_TOKEN" || key == "github_token") && value.isNotBlank()
        }
        ?.second
    if (!token.isNullOrBlank()) {
      return token
    }
  }
  return ""
}

val githubTokenForMetaSdk: String =
  sequenceOf(
    System.getenv("GITHUB_TOKEN"),
    System.getenv("github_token"),
    providers.gradleProperty("GITHUB_TOKEN").orNull,
    providers.gradleProperty("github_token").orNull,
    readGithubTokenFromEnvFiles(rootDir),
  )
    .map { it?.trim().orEmpty() }
    .firstOrNull { it.isNotEmpty() }
    .orEmpty()

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    maven {
      url = uri("https://maven.pkg.github.com/facebook/meta-wearables-dat-android")
      credentials {
        username = ""
        password = githubTokenForMetaSdk
      }
    }
  }
}

rootProject.name = "OpenClawNodeAndroid"
include(":app")
