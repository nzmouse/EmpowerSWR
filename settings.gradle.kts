pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://maven.google.com/") }
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://repo.itextsupport.com/release") }
        maven { url = uri("https://repository.liferay.com/nexus/content/repositories/public") } //Fallback

    }
}

rootProject.name = "EmpowerSWRApp0.2"
include(":app")
