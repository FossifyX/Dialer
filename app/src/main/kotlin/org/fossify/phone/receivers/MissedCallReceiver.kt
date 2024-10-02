package org.fossify.phone.receivers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import org.fossify.commons.extensions.getLaunchIntent
import org.fossify.commons.extensions.notificationManager
import org.fossify.commons.extensions.telecomManager
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.phone.R
import org.fossify.phone.helpers.MISSED_CALLS
import org.fossify.phone.helpers.MISSED_CALL_BACK
import org.fossify.phone.helpers.MISSED_CALL_CANCEL
import org.fossify.phone.helpers.MISSED_CALL_MESSAGE
import kotlin.random.Random

@RequiresApi(Build.VERSION_CODES.O)
class MissedCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val extras = intent.extras ?: return
        var phoneNumber = extras.getString("phoneNumber")
        var notificationId = extras.getInt("notificationId", -1)
        val notificationManager = context.notificationManager

        when (intent.action) {
            TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION -> {
                notificationId = Random.nextInt()
                phoneNumber = extras.getString(TelecomManager.EXTRA_NOTIFICATION_PHONE_NUMBER)
                val notificationCount = extras.getInt(TelecomManager.EXTRA_NOTIFICATION_COUNT)
                if (notificationCount != 0) {
                    createNotificationChannel(context)
                    notificationManager.notify(MISSED_CALLS.hashCode(), getNotificationGroup(context))
                    notificationManager.notify(notificationId, buildNotification(context, notificationId, phoneNumber ?: return))
                }
                null
            }

            MISSED_CALL_BACK -> phoneNumber?.let {
                Intent(Intent.ACTION_CALL).apply {
                    data = Uri.fromParts("tel", it, null)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }

            MISSED_CALL_MESSAGE -> phoneNumber?.let {
                Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.fromParts("sms", it, null)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }

            MISSED_CALL_CANCEL -> {
                context.telecomManager.cancelMissedCallsNotification()
                null
            }

            else -> null
        }?.let {
            Log.d("MISSEDCALL", it.toString())
            context.startActivity(it)
            context.notificationManager.cancel(notificationId)
        }
    }

    private fun createNotificationChannel(context: Context) {
        val notificationManager = context.notificationManager
        val channel = NotificationChannel(
            "missed_call_channel",
            context.getString(R.string.missed_call_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }

    private fun launchIntent(context: Context): PendingIntent {
        return PendingIntent.getActivity(
            context, 0, context.getLaunchIntent(), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getNotificationGroup(context: Context): Notification {
        return NotificationCompat.Builder(context, "missed_call_channel")
            .setSmallIcon(android.R.drawable.sym_call_missed)
            .setAutoCancel(true)
            .setGroupSummary(true)
            .setGroup(MISSED_CALLS)
            .setContentIntent(launchIntent(context))
            .build()
    }

    private fun buildNotification(context: Context, notificationId: Int, phoneNumber: String): Notification {
        val helper = SimpleContactsHelper(context)
        val name = helper.getNameFromPhoneNumber(phoneNumber)
        val photoUri = helper.getPhotoUriFromPhoneNumber(phoneNumber)

        val callBack = Intent(context, MissedCallReceiver::class.java).apply {
            action = MISSED_CALL_BACK
            putExtra("notificationId", notificationId)
            putExtra("phoneNumber", phoneNumber)
        }
        val callBackIntent = PendingIntent.getBroadcast(
            context, 0, callBack, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val smsIntent = Intent(context, MissedCallReceiver::class.java).apply {
            action = MISSED_CALL_MESSAGE
            putExtra("notificationId", notificationId)
            putExtra("phoneNumber", phoneNumber)
        }
        val messageIntent = PendingIntent.getBroadcast(
            context, 0, smsIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancel = Intent(context, MissedCallReceiver::class.java).apply {
            action = MISSED_CALL_CANCEL
            putExtra("notificationId", notificationId)
            putExtra("phoneNumber", phoneNumber)
        }
        val cancelIntent = PendingIntent.getActivity(
            context, 0, cancel, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, "missed_call_channel")
            .setSmallIcon(android.R.drawable.sym_call_missed)
            .setContentTitle(context.resources.getQuantityString(R.plurals.missed_calls, 1, 1))
            .setContentText(context.getString(R.string.missed_call_from, name))
            .setLargeIcon(Icon.createWithContentUri(photoUri))
            .setAutoCancel(true)
            .setGroup(MISSED_CALLS)
            .setContentIntent(launchIntent(context))
            .addAction(0, context.getString(R.string.call_back), callBackIntent)
            .addAction(0, context.getString(R.string.message), messageIntent)
            .setDeleteIntent(cancelIntent)
            .build()
    }
}
