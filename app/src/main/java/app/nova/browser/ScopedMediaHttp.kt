package app.nova.browser

import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Re-evaluate destination on every network hop, including redirects and subtitle/segment loads. */
object ScopedMediaHttp {
    fun factory(contexts: List<RequestContext>, active: RequestContext? = null): OkHttpDataSource.Factory {
        return OkHttpDataSource.Factory(client(contexts,active))
    }
    internal fun client(contexts:List<RequestContext>,active:RequestContext?=null):OkHttpClient {
        return OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS)
            .followSslRedirects(false)
            .addNetworkInterceptor { chain ->
                val target = chain.request().url.toString()
                val req = chain.request().newBuilder()
                RequestContext.ALLOWED.forEach { req.removeHeader(it) }
                val ctx = contexts.lastOrNull { sourceKey(it.url) == sourceKey(target) }
                    ?: active?.takeIf { it.matches(target) }
                ctx?.safeHeaders(target)?.forEach { (k,v) -> req.header(k,v) }
                if (ctx == null) req.header("User-Agent", "Nova/0.3")
                val response = chain.proceed(req.build())
                val type = response.header("Content-Type").orEmpty().substringBefore(';')
                val expected = Sniffer.classify(target)
                if (response.isSuccessful && expected in setOf("HLS","DASH","VIDEO","AUDIO") &&
                    type in setOf("text/html","application/json")) {
                    response.close(); throw java.io.IOException("The server returned a page, not media")
                }
                response
            }.build()
    }
}
