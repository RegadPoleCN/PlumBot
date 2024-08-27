package me.regadpole.plumbot

import taboolib.common.env.RuntimeDependencies
import taboolib.common.env.RuntimeDependency

@RuntimeDependencies(
    RuntimeDependency(value = "org.java-websocket:Java-WebSocket:1.5.5"),
    RuntimeDependency(value = "org.java-websocket:Java-WebSocket:1.5.7"),
    RuntimeDependency(value = "org.slf4j:slf4j-api:1.7.36"),
    RuntimeDependency(value = "uk.org.lidalia:sysout-over-slf4j:1.0.2"),
    RuntimeDependency(value = "net.kyori:event-method:3.0.0"),
    RuntimeDependency(value = "net.kyori:event-api:3.0.0"),
    RuntimeDependency(value = "org.checkerframework:checker-qual:2.5.4"),
    RuntimeDependency(value = "org.yaml:snakeyaml:2.0"),
    RuntimeDependency(value = "org.apache.logging.log4j:log4j-core:2.14.1"),
    RuntimeDependency(value = "org.junit.platform:junit-platform-engine:1.11.0"),
    RuntimeDependency(
        value = "com.github.RegadPoleCN:onebot-client:f73b158fc4",
        repository = "https://jitpack.io"
    )
)
object Dependencies {
}