buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.10.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory.get().asFile) // Updated to use modern API
}