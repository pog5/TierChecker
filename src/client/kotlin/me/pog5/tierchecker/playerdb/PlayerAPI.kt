package me.pog5.tierchecker.playerdb

import com.github.benmanes.caffeine.cache.Caffeine
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit

object PlayerAPI {
    const val BASE_URL = "https://playerdb.co/api/player/minecraft"
    const val USERAGENT = "TierChecker/1.0"

    val playerNameToUUIDCache = Caffeine.newBuilder()
        .maximumSize(600 * 5)
        .expireAfterAccess(5, TimeUnit.HOURS)
        .refreshAfterWrite(1, TimeUnit.MINUTES)
        .build<String, UUID?> { name -> getPlayer(name) }

    private val httpClient = HttpClient.newBuilder()
        .proxy(ProxySelector.getDefault())
        .followRedirects(HttpClient.Redirect.NORMAL)
        .priority(5)
        .build()

    private fun getPlayer(uuidOrName: String): UUID? {
        try {
            val uuid = UUID.fromString(uuidOrName)
            return uuid
        } catch (e: IllegalArgumentException) {
            val uuid = fetchUUID(uuidOrName) ?: return null
            return uuid
        }
    }

    fun fetchUUID(name: String): UUID? {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$BASE_URL/$name"))
            .header("User-Agent", USERAGENT)
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            return null
        }

        val body = response.body()
        val uuid = body.substringAfter("\"id\":\"").substringBefore("\"")
        return UUID.fromString(uuid)
    }

    fun getPlayerName(uuid: UUID): String? {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$BASE_URL/${uuid.toString().replace("-", "")}"))
            .header("User-Agent", USERAGENT)
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            return null
        }

        val body = response.body()
        val name = body.substringAfter("\"name\":\"").substringBefore("\"")
        return name
    }
}