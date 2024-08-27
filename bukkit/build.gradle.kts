taboolib { subproject = true }

dependencies {
    compileOnly(project(":common"))
}
gradle.buildFinished {
    buildDir.deleteRecursively()
}