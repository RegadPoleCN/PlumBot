taboolib {
    description {
        name(rootProject.name)
        desc("A qq bot plugin")
        links {
            name("homepage").url("https://github.com/RegadPoleCN/PlumBot")
        }
        contributors {
            name("alazeprt")
            name("RegadPole")
        }
    }
}

tasks {
    jar {
        // 构件名
        archiveBaseName.set(rootProject.name)
        // 打包子项目源代码
        rootProject.subprojects.forEach { from(it.sourceSets["main"].output) }
    }
}