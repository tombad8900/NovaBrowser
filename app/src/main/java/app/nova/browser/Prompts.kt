package app.nova.browser

import android.content.Context
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PromptDelegate as PD

/** Minimal page dialogs: alert, confirm, prompt and select lists. Popups are denied. */
class Prompts(private val ctx: Context) : PD {
    override fun onAlertPrompt(session: GeckoSession, p: PD.AlertPrompt): GeckoResult<PD.PromptResponse>? {
        val r = GeckoResult<PD.PromptResponse>()
        AlertDialog.Builder(ctx).setTitle(p.title ?: "").setMessage(p.message)
            .setPositiveButton("OK") { _, _ -> r.complete(p.dismiss()) }
            .setOnCancelListener { r.complete(p.dismiss()) }.show()
        return r
    }

    override fun onButtonPrompt(session: GeckoSession, p: PD.ButtonPrompt): GeckoResult<PD.PromptResponse>? {
        val r = GeckoResult<PD.PromptResponse>()
        AlertDialog.Builder(ctx).setTitle(p.title ?: "").setMessage(p.message)
            .setPositiveButton("OK") { _, _ -> r.complete(p.confirm(PD.ButtonPrompt.Type.POSITIVE)) }
            .setNegativeButton("Cancel") { _, _ -> r.complete(p.confirm(PD.ButtonPrompt.Type.NEGATIVE)) }
            .setOnCancelListener { r.complete(p.dismiss()) }.show()
        return r
    }

    override fun onTextPrompt(session: GeckoSession, p: PD.TextPrompt): GeckoResult<PD.PromptResponse>? {
        val r = GeckoResult<PD.PromptResponse>()
        val e = EditText(ctx).apply { setText(p.defaultValue ?: "") }
        AlertDialog.Builder(ctx).setTitle(p.title ?: "").setMessage(p.message).setView(e)
            .setPositiveButton("OK") { _, _ -> r.complete(p.confirm(e.text.toString())) }
            .setNegativeButton("Cancel") { _, _ -> r.complete(p.dismiss()) }
            .setOnCancelListener { r.complete(p.dismiss()) }.show()
        return r
    }

    override fun onChoicePrompt(session: GeckoSession, p: PD.ChoicePrompt): GeckoResult<PD.PromptResponse>? {
        val r = GeckoResult<PD.PromptResponse>()
        val items = p.choices.filter { !it.separator }
        val labels = items.map { it.label ?: "" }.toTypedArray()
        val b = AlertDialog.Builder(ctx).setTitle(p.title ?: p.message ?: "")
        if (p.type == PD.ChoicePrompt.Type.MULTIPLE) {
            val checked = BooleanArray(items.size) { items[it].selected }
            b.setMultiChoiceItems(labels, checked) { _, i, c -> checked[i] = c }
                .setPositiveButton("OK") { _, _ ->
                    r.complete(p.confirm(items.filterIndexed { i, _ -> checked[i] }.mapNotNull { it.id }.toTypedArray()))
                }
        } else {
            b.setItems(labels) { _, i -> r.complete(p.confirm(items[i])) }
        }
        b.setNegativeButton("Cancel") { _, _ -> r.complete(p.dismiss()) }
            .setOnCancelListener { r.complete(p.dismiss()) }.show()
        return r
    }

    override fun onPopupPrompt(session: GeckoSession, p: PD.PopupPrompt): GeckoResult<PD.PromptResponse>? =
        GeckoResult.fromValue(p.confirm(AllowOrDeny.DENY))
}
