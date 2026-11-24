package app.nova.browser

/** Main-thread gate: ready-first and session-first converge on exactly one attachment. */
internal class SessionAttachments<T:Any> {
    private val sessions=linkedSetOf<T>()
    private var attach:((T)->Unit)?=null
    fun register(session:T){if(sessions.add(session))attach?.invoke(session)}
    fun ready(action:(T)->Unit){
        if(attach!=null)return
        attach=action
        sessions.toList().forEach{if(it in sessions)action(it)}
    }
    fun unregister(session:T){sessions.remove(session)}
    fun clear(){sessions.clear();attach=null}
}
