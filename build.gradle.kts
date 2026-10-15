plugins {
    kotlin("jvm") version "1.9.22"
}

repositories {
    mavenCentral()
}

dependencies {
    // Подключаем локальный файл из папки libs, интернету больше не нужно качать ничего!
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
}