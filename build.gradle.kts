plugins {
    java
}

repositories {
    mavenCentral()

}

dependencies {

}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}