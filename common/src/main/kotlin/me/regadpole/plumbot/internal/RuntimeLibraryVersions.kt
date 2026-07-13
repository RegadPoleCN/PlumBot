package me.regadpole.plumbot.internal

/**
 * 运行时通过 Libby 下载的依赖版本。
 *
 * 本文件中所有版本号均为 [gradle/libs.versions.toml] 中对应库版本的镜像，
 * 修改时必须同步更新 gradle/libs.versions.toml，以保证编译期依赖与运行时下载依赖一致。
 */
object RuntimeLibraryVersions {
    const val SQLITE_JDBC = "3.49.0.0"
    const val MYSQL_CONNECTOR = "8.3.0"
    const val HIKARI_CP = "4.0.3"
    const val GUAVA = "21.0"
    const val TABOOLIB_DATABASE = "1.0.2"
    const val AONE_BOT = "1.0.11-beta"
    const val GSON = "2.11.0"
    const val CONFIGURATE_YAML = "4.2.0"
    const val CONFIGURATE_HOCON = "4.2.0"
    const val CONFIGURATE_EXTRA_KOTLIN = "4.2.0"
}
