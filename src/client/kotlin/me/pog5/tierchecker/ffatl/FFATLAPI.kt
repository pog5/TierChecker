package me.pog5.tierchecker.ffatl


import com.github.benmanes.caffeine.cache.Caffeine
import com.google.gson.Gson
import me.pog5.tierchecker.playerdb.PlayerAPI.USERAGENT
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.TimeUnit

object FFATLAPI {
    const val FFATL_API = "https://beez-server.vercel.app/api/search?ign="

    val tierCache = Caffeine.newBuilder()
        .maximumSize(600 * 5) // num of ppl expected in server times number of servers
        .expireAfterAccess(5, TimeUnit.MINUTES)
        .refreshAfterWrite(1, TimeUnit.MINUTES)
        .build<String, FFATLProfile?> { name -> getProfile(name.lowercase()) } // the api uses names instead of uuids, stupid imo

    private val httpClient = HttpClient.newBuilder()
        .proxy(ProxySelector.getDefault())
        .version(HttpClient.Version.HTTP_1_1)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .priority(6)
        .build()

    fun getProfile(name: String): FFATLProfile? {
        val url = "$FFATL_API${name}"

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", USERAGENT)
            .header("Accept", "application/json")
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build()

        val response: HttpResponse<String>
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: Exception) {
            println("HTTP Request failed: ${e.message}")
            e.printStackTrace()
            return null
        }

        println(request.uri())
        val body = response.body()
        println(response.body())
        if (response.statusCode() != 200 || response.body().contains("not found")) {
            return FFATLProfile(name = name)
        }
        val profile = fromJson(body)
        return profile
    }

    fun fromJson(json: String): FFATLProfile {
        val gson = Gson()
        return gson.fromJson(json, FFATLProfile::class.java)
    }

    fun toJson(profile: FFATLProfile?): String {
        val gson = Gson()
        return gson.toJson(profile)
    }
}