taboolib { subproject = true }

repositories {
    maven("https://repo.codemc.io/repository/maven-public/")
    maven("https://repo.glaremasters.me/repository/bloodshot")
}

dependencies {
    compileOnly(project(":common"))
    compileOnly("fr.xephi:authme:5.6.0-SNAPSHOT")
    compileOnly("org.maxgamer:QuickShop:5.1.1.2")
    compileOnly("com.ghostchu:quickshop-bukkit:4.2.2.11")
    compileOnly("com.griefdefender:api:2.1.0-SNAPSHOT")
//    compileOnly("com.bekvon.bukkit.residence:Residence:5.1.0.1")
    compileOnly(fileTree("lib"))
}
gradle.buildFinished {
    buildDir.deleteRecursively()
}