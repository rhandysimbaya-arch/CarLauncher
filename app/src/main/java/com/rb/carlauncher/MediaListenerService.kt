package com.rb.carlauncher

import android.service.notification.NotificationListenerService

/**
 * Intentionally empty. Enabling this notification listener in system settings is what
 * allows MediaSessionManager to hand the launcher the active media sessions of other apps.
 */
class MediaListenerService : NotificationListenerService()
