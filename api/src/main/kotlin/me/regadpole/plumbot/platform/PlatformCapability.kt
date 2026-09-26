package me.regadpole.plumbot.platform

import me.regadpole.plumbot.api.PublicApi

/**
 * Minecraft 服务端平台能力矩阵。
 * 用于抹平 Bukkit / Velocity / BungeeCord / Fabric 等不同运行环境的底层能力差异。
 */
@PublicApi
enum class PlatformCapability {

    // ================= 1. 聊天与消息维度 =================
    /** 服务端聊天监听能力 */
    CHAT_RECEIVE,

    /** 全服富文本广播能力 */
    CHAT_BROADCAST,

    // ================= 2. 玩家生命周期与鉴权拦截 =================
    /** 玩家握手鉴权阶段拦截能力（白名单未绑定踢出） */
    PRE_LOGIN_INTERCEPT,

    /** 真实进服完成事件（实体生成/连接就绪，用于触发真实的进服广播） */
    PLAYER_JOIN_BROADCAST,

    /** 玩家离服事件感知 */
    PLAYER_QUIT_BROADCAST,

    /** 跨服切换感知（仅代理端路由支持，单服不支持） */
    SERVER_SWITCH_BROADCAST,

    // ================= 3. 游戏机制与实体事件（单服后端专有） =================
    /** 玩家死亡广播（依赖实体伤害计算，单服专属） */
    PLAYER_DEATH_BROADCAST,

    /** 玩家成就/进度达成广播（依赖世界进度追踪，单服专属） */
    PLAYER_ADVANCEMENT_BROADCAST,

    // ================= 4. 运行时负载与控制台交互 =================
    /** 服务器性能指标读取能力（TPS、MSPT、内存） */
    SERVER_TPS_METRICS,

    /** 服务端主线程控制台指令调度能力 */
    COMMAND_DISPATCH,
}
