    package me.pog5.mctiersio

    import com.github.benmanes.caffeine.cache.Caffeine
    import com.google.gson.Gson
    import me.pog5.playerdb.PlayerAPI.USERAGENT
    import java.net.ProxySelector
    import java.net.URI
    import java.net.http.HttpClient
    import java.net.http.HttpRequest
    import java.net.http.HttpResponse
    import java.time.Duration
    import java.util.UUID
    import java.util.concurrent.TimeUnit

    object MCTIOAPI {
        const val MCT_API = "https://mctiers.io/api/profile"

        val tierCache = Caffeine.newBuilder()
            .maximumSize(600 * 5) // num of ppl expected in server times number of servers
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .refreshAfterWrite(1, TimeUnit.MINUTES)
            .build<UUID, MCTIOProfile?>{ uuid -> getProfile(uuid) }

        private val httpClient = HttpClient.newBuilder()
            .proxy(ProxySelector.getDefault())
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .priority(6)
            .build()

        fun getProfile(uuid: UUID): MCTIOProfile? {
            val dashlessUUID = uuid.toString().replace("-", "")
            val url = "$MCT_API/${dashlessUUID}"

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

            val body = response.body()

            if (response.statusCode() != 200) {
                return null
            }
            val profile = fromJson(body)
            return profile
        }

        fun fromJson(json: String): MCTIOProfile {
            val gson = Gson()
            return gson.fromJson(json, MCTIOProfile::class.java)
        }

        fun toJson(profile: MCTIOProfile): String {
            val gson = Gson()
            return gson.toJson(profile)
        }
    }