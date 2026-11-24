package app.nova.browser

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/** All Gecko engine setup in one place so the UI never touches runtime settings directly. */
object Engine {
    const val EXT_ID = "nova-detector@nova.app"
    private const val TAG = "NovaEngine"
    private var runtime: GeckoRuntime? = null
    @Volatile var ext: WebExtension? = null
    val observations = java.util.Collections.synchronizedMap(LinkedHashMap<String, org.json.JSONObject>())
    private val waiting = mutableListOf<(WebExtension) -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    fun get(ctx: Context): GeckoRuntime {
        runtime?.let { return it }
        val cb = ContentBlocking.Settings.Builder()
            .enhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.STRICT)
            .antiTracking(ContentBlocking.AntiTracking.AD or ContentBlocking.AntiTracking.ANALYTIC or
                ContentBlocking.AntiTracking.SOCIAL or ContentBlocking.AntiTracking.CRYPTOMINING or
                ContentBlocking.AntiTracking.FINGERPRINTING or ContentBlocking.AntiTracking.STP)
            .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
            .cookiePurging(true)
            .queryParameterStrippingEnabled(true)
            .strictSocialTrackingProtection(true)
            .allowListBaselineTrackingProtection(true)
            .allowListConvenienceTrackingProtection(true)
            .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
            .build()
        val settings = GeckoRuntimeSettings.Builder()
            .contentBlocking(cb)
            .globalPrivacyControlEnabled(true)
            .aboutConfigEnabled(false)
            .remoteDebuggingEnabled(false)
            .consoleOutput(true)
            .build()
        val rt = GeckoRuntime.create(ctx.applicationContext, settings)
        runtime = rt
        AdBlock.start(rt)
        rt.webExtensionController.ensureBuiltIn("resource://android/assets/nova-ext/", EXT_ID).accept({ e ->
            Log.i(TAG, "detector extension ready: ${e?.id}")
            if (e != null) {
                e.setMessageDelegate(object : WebExtension.MessageDelegate {
                    override fun onMessage(nativeApp: String, message: Any, sender: WebExtension.MessageSender): GeckoResult<Any>? {
                        if (nativeApp != "nova" || sender.environmentType != WebExtension.MessageSender.ENV_TYPE_EXTENSION || message !is org.json.JSONObject) return null
                        if (message.optString("t") == "resource") synchronized(observations) {
                            if (observations.size >= 600) observations.remove(observations.keys.first())
                            observations[message.optString("token")] = message
                        }
                        return null
                    }
                }, "nova")
                ext = e; main.post { val l = waiting.toList(); waiting.clear(); l.forEach { it(e) } } }
        }, { t -> Log.e(TAG, "detector extension failed", t) })
        return rt
    }

    fun whenReady(cb: (WebExtension) -> Unit) {
        val e = ext
        if (e != null) cb(e) else waiting.add(cb)
    }

}
