package com.maya.assistant

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class MayaNotificationListener : NotificationListenerService() {

    companion object {
        @Volatile var lastWhatsApp: String? = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == "com.whatsapp") {
            val e = sbn.notification.extras
            val who = e.getCharSequence("android.title")
            val msg = e.getCharSequence("android.text")
            lastWhatsApp = "$who: $msg"
        }
    }
}
