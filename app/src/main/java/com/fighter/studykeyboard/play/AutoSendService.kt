package com.fighter.studykeyboard.play

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.fighter.studykeyboard.util.DebugLog

/**
 * Optional helper that clicks the real "Send" button of the app that is in front.
 *
 * Some apps (WhatsApp chat, YouTube comments, FB comments, Instagram DM) treat the
 * Enter key as "new line", so pressing Enter can never send a message there. With
 * this service enabled, the keyboard finds the app's own send/post button and
 * clicks it - exactly like a normal user tap, one tap per note.
 *
 * It only reacts when the user presses Play (AutoSendService.requestSend is called
 * from the keyboard then). It never reads, stores or uploads screen content.
 */
class AutoSendService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
        DebugLog.log("AutoSend", "service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        DebugLog.log("AutoSend", "service destroyed")
        if (instance === this) instance = null
        super.onDestroy()
    }

    /** Finds something that looks like a send/post button and clicks it. */
    fun clickSend(): Boolean {
        val roots = ArrayList<AccessibilityNodeInfo>()
        rootInActiveWindow?.let { roots.add(it) }
        val windows = try {
            windows
        } catch (error: Exception) {
            emptyList<android.view.accessibility.AccessibilityWindowInfo>()
        }
        for (window in windows) {
            val root = try {
                window.root
            } catch (error: Exception) {
                null
            }
            if (root != null && root !in roots) roots.add(root)
        }
        DebugLog.log("AutoSend", "searching send button in ${roots.size} window(s)")
        for (root in roots) {
            val node = findSendNode(root, 0)
            if (node != null) {
                val parent = node.parent
                val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK) ||
                    (parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false)
                DebugLog.log("AutoSend", "clicked id=${node.viewIdResourceName} desc=${node.contentDescription} ok=$clicked")
                if (clicked) return true
            }
        }
        return false
    }

    private fun findSendNode(node: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
        if (node == null || depth > MAX_DEPTH) return null
        if (!node.isVisibleToUser) return null
        if (isSendButton(node)) return node
        for (index in 0 until node.childCount) {
            val child = try {
                node.getChild(index)
            } catch (error: Exception) {
                null
            }
            val found = findSendNode(child, depth + 1)
            if (found != null) return found
        }
        return null
    }

    private fun isSendButton(node: AccessibilityNodeInfo): Boolean {
        val viewId = node.viewIdResourceName.orEmpty().lowercase()
        val idTail = viewId.substringAfterLast("/")
        val description = node.contentDescription?.toString().orEmpty().lowercase()
        val label = node.text?.toString().orEmpty().trim().lowercase()

        if (idTail in SEND_IDS) return true
        if (description in SEND_LABELS) return true
        if (label in SEND_LABELS) return true
        if (description.contains("send") && description.length <= 20) return true
        if (label.contains("send") && label.length <= 20) return true
        return false
    }

    companion object {
        private const val MAX_DEPTH = 40

        private val SEND_IDS = setOf(
            "send", "send_button", "sendbutton", "send_btn", "btn_send", "sends",
            "comment_send", "post", "submit", "submit_button", "reply_send",
            "row_send", "send_message", "message_send", "composer_send",
            "action_send", "sendbutton_container", "send_btn_container", "send_button_container"
        )

        private val SEND_LABELS = setOf(
            "send", "send message", "post", "comment", "reply", "publish", "submit",
            "भेजें", "भेजो", "पोस्ट करें", "टिप्पणी करें", "जवाब दें"
        )

        @Volatile
        var instance: AutoSendService? = null

        fun isEnabled(context: Context): Boolean {
            if (instance != null) return true
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            if (TextUtils.isEmpty(enabled)) return false
            val target = "${context.packageName}/${AutoSendService::class.java.name}"
            return enabled.split(':').any { it.equals(target, ignoreCase = true) }
        }

        /** Clicks the app's send button if the service is running. */
        fun requestSend(): Boolean {
            val service = instance ?: return false
            return try {
                service.clickSend()
            } catch (error: Exception) {
                false
            }
        }
    }
}
