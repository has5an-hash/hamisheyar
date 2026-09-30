package ir.hamisheyar.app.web

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class SearchHit(val title: String, val url: String, val snippet: String)

object WebResearchService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun search(query: String, limit: Int = 4): List<SearchHit> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val request = Request.Builder()
            .url("https://html.duckduckgo.com/html/?q=$encoded")
            .header("User-Agent", "Mozilla/5.0 (Android; Hamisheyar/0.1)")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            val html = response.body?.string().orEmpty()
            val doc = Jsoup.parse(html)
            doc.select(".result").mapNotNull { result ->
                val a = result.selectFirst(".result__a") ?: return@mapNotNull null
                val snippet = result.selectFirst(".result__snippet")?.text().orEmpty()
                SearchHit(
                    title = a.text().trim(),
                    url = cleanUrl(a.attr("href")),
                    snippet = snippet.trim()
                )
            }.filter { it.title.isNotBlank() }.take(limit)
        }
    }

    fun asContext(hits: List<SearchHit>): String = hits.mapIndexed { index, hit ->
        (index + 1).toString() + ") " + hit.title + "\n" + hit.snippet + "\n" + hit.url
    }.joinToString("\n\n")

    private fun cleanUrl(href: String): String {
        val marker = "uddg="
        val idx = href.indexOf(marker)
        if (idx == -1) return href
        val encoded = href.substring(idx + marker.length).substringBefore('&')
        return runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrDefault(href)
    }
}
