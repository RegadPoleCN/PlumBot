package me.regadpole.plumbot.api.config

class Messages: Cloneable {
    lateinit var prefix: String

    lateinit var load: String
    lateinit var unload: String

    lateinit var ob2server: String
    lateinit var server2ob: String

    lateinit var playerList: String

    lateinit var joinProxy: String
    lateinit var leaveProxy: String
    lateinit var changeServer: String

    lateinit var kickServer: String
    lateinit var kickPlatform: String

    lateinit var playerAddBind: String
    lateinit var playerDeleteBind: String
    lateinit var playerQueryBind: String

    lateinit var fullBind: String
    lateinit var qqEmptyBind: String
    lateinit var idEmptyBind: String
    lateinit var existsBind: String
    lateinit var notExistsBind: String
    lateinit var notBelongToYou: String
    lateinit var wrongUsage: String

    lateinit var adminAddBind: String
    lateinit var adminDeleteBind: String
    lateinit var adminQueryIdBind: String
    lateinit var adminQueryQQBind: String

    lateinit var help: List<String>

    lateinit var noCommandFound: String
    lateinit var commandAddBind: String
    lateinit var commandDeleteBindById: String
    lateinit var commandDeleteBindByQQ: String
    lateinit var commandQueryBindById: String
    lateinit var commandQueryBindByQQ: String

    public override fun clone(): Messages {
        return super.clone() as Messages
    }
}