taboolib { subproject = true }

gradle.buildFinished {
    buildDir.deleteRecursively()
}