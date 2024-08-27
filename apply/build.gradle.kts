taboolib {
    description {
        name(rootProject.name)
        desc("A bot plugin for QQ/Kook")
        links {
            name("homepage").url("https://github.com/RegadPoleCN/PlumBot")
        }
        contributors {
            name("RegadPole")
            name("alazeprt")
        }
        prefix("PlumBot")
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