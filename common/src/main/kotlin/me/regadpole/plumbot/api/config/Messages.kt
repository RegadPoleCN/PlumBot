package me.regadpole.plumbot.api.config

object Messages : Cloneable {
    var prefix: String = "<missing:prefix>"

    var load: String = "<missing:load>"
    var unload: String = "<missing:unload>"

    var ob2server: String = "<missing:ob2server>"
    var server2ob: String = "<missing:server2ob>"

    var playerList: String = "<missing:playerList>"

    var joinProxy: String = "<missing:joinProxy>"
    var leaveProxy: String = "<missing:leaveProxy>"
    var changeServer: String = "<missing:changeServer>"

    var kickServer: String = "<missing:kickServer>"
    var kickPlatform: String = "<missing:kickPlatform>"

    var playerAddBind: String = "<missing:playerAddBind>"
    var playerDeleteBind: String = "<missing:playerDeleteBind>"
    var playerQueryBind: String = "<missing:playerQueryBind>"

    var fullBind: String = "<missing:fullBind>"
    var qqEmptyBind: String = "<missing:qqEmptyBind>"
    var idEmptyBind: String = "<missing:idEmptyBind>"
    var existsBind: String = "<missing:existsBind>"
    var notExistsBind: String = "<missing:notExistsBind>"
    var notBelongToYou: String = "<missing:notBelongToYou>"
    var wrongUsage: String = "<missing:wrongUsage>"
    var internalError: String = "<missing:internalError>"

    var adminAddBind: String = "<missing:adminAddBind>"
    var adminDeleteBind: String = "<missing:adminDeleteBind>"
    var adminQueryIdBind: String = "<missing:adminQueryIdBind>"
    var adminQueryQQBind: String = "<missing:adminQueryQQBind>"

    var help: List<String> = emptyList()

    var noCommandFound: String = "<missing:noCommandFound>"
    var commandAddBind: String = "<missing:commandAddBind>"
    var commandDeleteBindById: String = "<missing:commandDeleteBindById>"
    var commandDeleteBindByQQ: String = "<missing:commandDeleteBindByQQ>"
    var commandQueryBindById: String = "<missing:commandQueryBindById>"
    var commandQueryBindByQQ: String = "<missing:commandQueryBindByQQ>"

    public override fun clone(): Messages {
        return super.clone() as Messages
    }
}
