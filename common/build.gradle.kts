plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    api(project(":api"))
    // 渲染门户仅由 :common 内部使用；改为 implementation，避免把 :render 的可见面
    // 泄漏给所有下游模块（适配层因此彻底看不到渲染实现类）。
    implementation(project(":render"))
    api(libs.bundles.kotlinxEcosystem)
    // gson
    compileOnly(libs.gson)
    // config
    compileOnly(libs.configurateYaml)
    compileOnly(libs.configurateExtraKotlin)
    // cache
    compileOnly(libs.aedile)
    // database
    compileOnly(libs.taboolibDatabase)
    compileOnly(libs.hikaricp)
    compileOnly(libs.mysql)
    compileOnly(libs.sqlite)
    // kotlin
    implementation(kotlin("reflect"))
    testImplementation(kotlin("test"))
    // 迁移测试需要真实解析 YAML（生产环境为 compileOnly）
    testImplementation(libs.configurateYaml)
    // 指令层测试需要构造 PlatformContext 替身（其 sendMessage 形参是 adventure Component）
    testImplementation(libs.adventureApi)
    // 消息转发路径会渲染 MiniMessage（生产为 compileOnly）
    testImplementation(libs.adventureTextMinimessage)
    // 数据库测试需要真实建表/查询：sqlite 驱动、连接池与 taboolib 表 DSL 在生产是 compileOnly
    testImplementation(libs.sqlite)
    testImplementation(libs.hikaricp)
    testImplementation(libs.taboolibDatabase)
    // 适配器生命周期测试需要构造 AbstractBotAdapter 子类，其缓存层依赖 aedile/caffeine（生产为 compileOnly）
    testImplementation(libs.aedile)
    // 词库网络分支测试要构造 FilterThesaurusManager，其 JSON 解析依赖 gson（生产为 compileOnly）
    testImplementation(libs.gson)
    // adventure
    compileOnly(libs.adventureApi)
    compileOnly(libs.adventureTextMinimessage)
    compileOnly(libs.adventureTextSerializerLegacy)
    // libby
    implementation(libs.libbyCore)
}
