package app.nova.browser

import org.mozilla.geckoview.*

/** Installs Mozilla's signed, unmodified uBlock Origin. No filter engine is copied into Nova. */
object AdBlock {
    const val ID="uBlock0@raymondhill.net"
    private const val XPI="https://addons.mozilla.org/firefox/downloads/file/5290541/ublock_origin-1.77.0.xpi"
    var extension:WebExtension?=null
    var state="Starting"
    private val waiting=mutableListOf<()->Unit>()
    private val actions=java.util.WeakHashMap<GeckoSession,WebExtension.Action>()
    fun start(rt:GeckoRuntime) {
        val c=rt.webExtensionController
        c.setPromptDelegate(object:WebExtensionController.PromptDelegate {
            override fun onInstallPromptRequest(e:WebExtension,permissions:Array<String>,origins:Array<String>,data:Array<String>):GeckoResult<WebExtension.PermissionPromptResponse> =
                GeckoResult.fromValue(WebExtension.PermissionPromptResponse(e.id==ID,false,false))
        })
        c.list().accept({all ->
            val existing=all?.firstOrNull{it.id==ID}
            if(existing!=null){ready(existing);return@accept}
            state="Installing"
            c.install(XPI).accept({e->if(e!=null)ready(e) else {state="Unavailable"}}, {_->state="Unavailable";android.util.Log.w("NovaEngine","Ad filter installation unavailable")})
        },{_->state="Unavailable"})
    }
    private fun ready(e:WebExtension) {
        extension=e;state=if(e.metaData.enabled)"On" else "Off"
        android.util.Log.i("NovaEngine","Ad filter ready enabled=${e.metaData.enabled}")
        val tasks=waiting.toList();waiting.clear();tasks.forEach{it()}
    }
    fun attach(session:GeckoSession,openPopup:(GeckoSession)->Unit) {
        val e=extension?:run{waiting.add{attach(session,openPopup)};return}
        session.webExtensionController.setActionDelegate(e,object:WebExtension.ActionDelegate {
            override fun onBrowserAction(e:WebExtension,s:GeckoSession?,action:WebExtension.Action){actions[session]=action}
            override fun onTogglePopup(e:WebExtension,action:WebExtension.Action):GeckoResult<GeckoSession> {
                val popup=GeckoSession();openPopup(popup);return GeckoResult.fromValue(popup)
            }
        })
    }
    fun show(session:GeckoSession):Boolean {val action=actions[session]?:return false;action.click();return true}
}
