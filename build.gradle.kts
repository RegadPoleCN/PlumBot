import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

val copyDist = tasks.register<Copy>("dist") {
    group = "distribution"
    description = "Collects distribution ShadowJars from all platform modules into /dist"
    destinationDir = file("dist")
}

subprojects {
    plugins.withId("com.gradleup.shadow") {
        val shadowJarTask = tasks.named<ShadowJar>("shadowJar")
        copyDist.configure {
            from(shadowJarTask)
        }
    }
}
