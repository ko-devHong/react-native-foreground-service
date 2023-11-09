/*
 * Copyright (c) 2011-2019, Zingaya, Inc. All rights reserved.
 */

package com.leetaehong.foregroundservice;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReadableMap;

import static com.leetaehong.foregroundservice.Constants.ERROR_ANDROID_VERSION;
import static com.leetaehong.foregroundservice.Constants.ERROR_INVALID_CONFIG;

import androidx.annotation.RequiresApi;

class NotificationHelper {
    private static NotificationHelper instance = null;
    private NotificationManager mNotificationManager;

    public enum NotificationType {
        FOREGROUND,
        BACKGROUND
    }

    public static synchronized NotificationHelper getInstance(Context context) {
        if (instance == null) {
            instance = new NotificationHelper(context);
        }
        return instance;
    }

    private NotificationHelper(Context context) {
        mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

    }

    void createNotificationChannel(ReadableMap channelConfig, Promise promise) {
        if (channelConfig == null) {
            Log.e("NotificationHelper", "createNotificationChannel: invalid config");
            promise.reject(ERROR_INVALID_CONFIG, "LTForegroundService: Channel config is invalid");
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!channelConfig.hasKey("id")) {
                promise.reject(ERROR_INVALID_CONFIG, "LTForegroundService: Channel id is required");
                return;
            }
            String channelId = channelConfig.getString("id");
            if (!channelConfig.hasKey("name")) {
                promise.reject(ERROR_INVALID_CONFIG, "LTForegroundService: Channel name is required");
                return;
            }
            String channelName = channelConfig.getString("name");
            String channelDescription = channelConfig.getString("description");
            int channelImportance = channelConfig.hasKey("importance") ?
                    channelConfig.getInt("importance") : NotificationManager.IMPORTANCE_LOW;
            boolean enableVibration = channelConfig.hasKey("enableVibration") && channelConfig.getBoolean("enableVibration");
            if (channelId == null || channelName == null) {
                promise.reject(ERROR_INVALID_CONFIG, "LTForegroundService: Channel id or name is not specified");
                return;
            }
            NotificationChannel channel = new NotificationChannel(channelId, channelName, channelImportance);
            channel.setDescription(channelDescription);
            channel.enableVibration(enableVibration);
            mNotificationManager.createNotificationChannel(channel);
            promise.resolve(null);
        } else {
            promise.reject(ERROR_ANDROID_VERSION, "LTForegroundService: Notification channel can be created on Android O+");
        }
    }

    Notification buildNotification(Context context, Bundle notificationConfig,NotificationType notificationType) {
        if (notificationConfig == null) {
            Log.e("NotificationHelper", "buildNotification: invalid config");
            return null;
        }
        Class mainActivityClass = getMainActivityClass(context);
        if (mainActivityClass == null) {
            return null;
        }
        Intent notificationIntent = new Intent(context, mainActivityClass);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder notificationBuilder;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = notificationConfig.getString("channelId");
            if (channelId == null) {
                Log.e("NotificationHelper", "buildNotification: invalid channelId");
                return null;
            }
            notificationBuilder = new Notification.Builder(context, channelId);
        } else {
            notificationBuilder = new Notification.Builder(context);
        }

        int priorityInt = notificationConfig.containsKey("priority") ? notificationConfig.getInt("priority") : Notification.PRIORITY_HIGH;

        int priority;
        switch (priorityInt) {
            case 0:
                priority = Notification.PRIORITY_DEFAULT;
                break;
            case -1:
                priority = Notification.PRIORITY_LOW;
                break;
            case -2:
                priority = Notification.PRIORITY_MIN;
                break;
            case 1:
                priority = Notification.PRIORITY_HIGH;
                break;
            case 2:
                priority = Notification.PRIORITY_MAX;
                break;
            default:
                priority = Notification.PRIORITY_HIGH;
                break;

        }

        notificationBuilder.setContentTitle(notificationConfig.getString("title"))
                .setContentText(notificationConfig.getString("text"))
                .setPriority(priority)
                .setContentIntent(pendingIntent);

        Boolean ongoing = notificationConfig.getBoolean("ongoing");
        if (ongoing == null) {
            ongoing = false;
        }

        if(ongoing) {
            notificationBuilder.setOngoing(true);
            notificationBuilder.setAutoCancel(false);
        }

        if(NotificationType.BACKGROUND.equals(notificationType)) {
            final PendingIntent contentIntent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                contentIntent = PendingIntent.getActivity(context, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            } else {
                contentIntent = PendingIntent.getActivity(context, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            }
            notificationBuilder.setContentIntent(contentIntent);
        }

        String iconName = notificationConfig.getString("icon");
        if (iconName != null) {
            notificationBuilder.setSmallIcon(getResourceIdForResourceName(context, iconName));
        }

        return notificationBuilder.build();
    }

    /**
     * This is the method that can be called to update the Notification
     */
    void updateNotification(int notificationId, Notification notification) {
        mNotificationManager.notify(notificationId, notification);
    }

    Boolean validCheckNotificationConfig(ReadableMap notificationConfig, Promise promise) {
        if (notificationConfig == null) {
            promise.reject(ERROR_INVALID_CONFIG, "LTForegroundService: Notification config is invalid");
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!notificationConfig.hasKey("channelId")) {
                promise.reject(ERROR_INVALID_CONFIG, "LTForegroundService: channelId is required");
                return false;
            }
        }

        if (!notificationConfig.hasKey("id")) {
            promise.reject(ERROR_INVALID_CONFIG, "LTForegroundService: id is required");
            return false;
        }

        if (!notificationConfig.hasKey("icon")) {
            promise.reject(ERROR_INVALID_CONFIG, "LTForegroundService: icon is required");
            return false;
        }

        if (!notificationConfig.hasKey("title")) {
            promise.reject(ERROR_INVALID_CONFIG, "LTForegroundService: title is reqired");
            return false;
        }

        if (!notificationConfig.hasKey("text")) {
            promise.reject(ERROR_INVALID_CONFIG, "LTForegroundService: text is required");
            return false;
        }

        return true;
    }


    private Class getMainActivityClass(Context context) {
        String packageName = context.getPackageName();
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        if (launchIntent == null || launchIntent.getComponent() == null) {
            Log.e("NotificationHelper", "Failed to get launch intent or component");
            return null;
        }
        try {
            return Class.forName(launchIntent.getComponent().getClassName());
        } catch (ClassNotFoundException e) {
            Log.e("NotificationHelper", "Failed to get main activity class");
            return null;
        }
    }

    private int getResourceIdForResourceName(Context context, String resourceName) {
        int resourceId = context.getResources().getIdentifier(resourceName, "drawable", context.getPackageName());
        if (resourceId == 0) {
            resourceId = context.getResources().getIdentifier(resourceName, "mipmap", context.getPackageName());
        }
        return resourceId;
    }
}