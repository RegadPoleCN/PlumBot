package me.regadpole.plumbot.utils

import com.google.gson.Gson
import com.google.gson.JsonObject
import jdk.internal.org.jline.utils.InputStreamReader
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object Formatter {

    private val remoteFilter = mutableMapOf<String, List<String>>()

    fun initialUrl(contents: List<String>) {
        remoteFilter.clear()
        runTaskAsync {
            contents.forEach { string ->
                val pattern = Regex("\\$(regex|filter|replaceTo|url|path):\\{([^ ]+)\\}")
                val keyValueMap = mutableMapOf<String, String>()
                for (match in pattern.findAll(string)) {
                    val key = match.groupValues[1].replace("[[space]]", " ")
                    val value = match.groupValues[2].replace("[[space]]", " ")
                    keyValueMap[key] = value
                }
                val urlString = keyValueMap["url"]
                val url = URL(urlString ?: return@forEach)
                val path = keyValueMap["path"] ?: "words"
                val connection = url.openConnection() as? HttpsURLConnection ?: URL(urlString).openConnection() as HttpURLConnection
                val response = StringBuilder()
                try {
                    connection.requestMethod = "GET"
                    val responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val reader = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8"))
                        var inputLine: String?
                        while (reader.readLine().also { inputLine = it } != null) {
                            response.append(inputLine)
                        }
                        reader.close()
                    } else {
                        error("Cannot get filter from url: $url, response code: ${connection.responseCode}, " +
                                "response message: ${connection.responseMessage}")
                        return@forEach
                    }
                    connection.disconnect()
                    if (response.isEmpty()) {
                        warn("Cannot get valid information from url: $url, the response content is empty!")
                        return@forEach
                    }
                    val filters = mutableListOf<String>()
                    val gson = Gson()
                    var jsonObject = gson.fromJson(response.toString(), JsonObject::class.java)
                    for (i in 0..(path.split(".").size - 2)) {
                        jsonObject = jsonObject.getAsJsonObject(path.split(".")[i])
                    }
                    jsonObject.getAsJsonArray(path.split(".").last()).forEach {
                        filters.add(it.asString)
                    }
                    remoteFilter["$urlString.$path"] = filters
                } catch (e: Exception) {
                    debug("Failed to get remote filter: $urlString.$path: $e")
                    e.printStackTrace()
                }
            }
        }
    }

    fun regexFilter(formatter: String, string: String): String {
        val pattern = Regex("\\$(regex|filter|replaceTo|url|path):\\{([^ ]+)\\}")
        val keyValueMap = mutableMapOf<String, String>()
        for (match in pattern.findAll(formatter)) {
            val key = match.groupValues[1].replace("[[space]]", " ")
            val value = match.groupValues[2].replace("[[space]]", " ")
            keyValueMap[key] = value
        }
        val regex = keyValueMap["regex"]
        val filter = keyValueMap["filter"]
        val url = keyValueMap["url"]
        val path = keyValueMap["path"] ?: "words"
        val replaceTo = keyValueMap["replaceTo"] ?: ""
        if (regex != null) {
            return string.replace(Regex(regex), replaceTo)
        } else if (filter != null) {
            return string.replace(filter, replaceTo)
        } else if (url != null) {
            var newString = string
            (remoteFilter["$url.$path"]?: return string).forEach {
                newString = newString.replace(it, replaceTo)
            }
            return newString
        }
        return string
    }

    fun regexFilter(formatter: List<String>, string: String): String {
        var newString = string
        formatter.forEach {
            newString = regexFilter(it, newString)
        }
        return newString
    }

    fun pluginClear(string: String): String {
        return string.replace(Regex("&([0-9a-fklmnor])"), "")
    }

    fun pluginToChat(string: String): String {
        return string.replace(Regex("&([0-9a-fklmnor])")) { matchResult ->
            "§" + matchResult.groupValues[1]
        }
    }

    fun chatClear(string: String): String {
        return string.replace(Regex("§([0-9a-fklmnor])"), "")
    }
}