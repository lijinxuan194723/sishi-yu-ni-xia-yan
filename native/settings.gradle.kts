pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "FourSeasonsNative"
include(":app", ":core", ":designsystem", ":feature:home", ":feature:chat", ":feature:moments", ":feature:plans", ":feature:journal", ":feature:timer", ":feature:settings")
