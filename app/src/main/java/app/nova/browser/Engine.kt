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
        rt.webExtensionController.promptDelegate = object : WebExtensionController.PromptDelegate {
            override fun onInstallPromptRequest(e: WebExtension, perms: Array<out String>, origins: Array<out String>, data: Array<out String>): GeckoResult<WebExtension.PermissionPromptResponse>? {
                Log.i(TAG, "install prompt for ${e.id}: perms=${perms.toList()} origins=${origins.toList()}")
                return GeckoResult.fromValue(WebExtension.PermissionPromptResponse(true, false, false))
            }
        }
        rt.webExtensionController.ensureBuiltIn("resource://android/assets/nova-ext/", EXT_ID).accept({ e ->
            Log.i(TAG, "detector extension ready: ${e?.id}")
            if (e != null) { ext = e; main.post { val l = waiting.toList(); waiting.clear(); l.forEach { it(e) } } }
        }, { t -> Log.e(TAG, "detector extension failed", t) })
        return rt
    }

    fun whenReady(cb: (WebExtension) -> Unit) {
        val e = ext
        if (e != null) cb(e) else waiting.add(cb)
    }

    /** Experimental: installs uBlock Origin from AMO and reports the outcome. Not part of the default setup. */
    fun installUblock(ctx: Context, report: (String) -> Unit) {
        val rt = get(ctx)
        rt.webExtensionController.install("https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi").accept({ e ->
            Log.i(TAG, "UBLOCK_INSTALL_OK id=${e?.id} name=${e?.metaData?.name}")
            main.post { report("uBlock Origin installed") }
        }, { t ->
            Log.e(TAG, "UBLOCK_INSTALL_FAILED", t)
            main.post { report("uBlock Origin install failed: ${t?.message}") }
        })
    }
}
