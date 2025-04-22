package com.appit.cordova.geofence;

import android.app.NotificationManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.PersistableBundle;
import android.util.Log;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

// https://codelabs.developers.google.com/codelabs/background-location-updates-android-o/#4
public class ReceiveTransitionsReceiver extends BroadcastReceiver {
    protected static final String GeofenceTransitionIntent = "com.appit.cordova.geofence.TRANSITION";
    protected BeepHelper beepHelper;
    protected GeoNotificationNotifier notifier;
    protected GeoNotificationStore store;

    /**
     * Handles incoming intents
     *
     * @param intent The Intent sent by Location Services. This Intent is provided
     *               to Location Services (inside a PendingIntent) when you call
     *               addGeofences()
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        beepHelper = new BeepHelper();
        store = new GeoNotificationStore(context);
        Logger.setLogger(new Logger(GeofencePlugin.TAG, context, false));
        Logger logger = Logger.getLogger();
        logger.log(Log.DEBUG, "ReceiveTransitionsIntentService - onHandleIntent");
        Intent broadcastIntent = new Intent(GeofenceTransitionIntent);
		
		// Required for implicit BroadcastReceiver to work for SDK 26+
        String packageName = context.getPackageName();
        broadcastIntent.setPackage(packageName);
		
        notifier = new GeoNotificationNotifier(
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE),
                context
        );

        try {
            // TODO: refactor this, too long
            // First check for errors
            GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);
            if (geofencingEvent.hasError()) {
                // Get the error code with a static method
                int errorCode = geofencingEvent.getErrorCode();
                String error = "Location Services error: " + Integer.toString(errorCode);
                // Log the error
                logger.log(Log.ERROR, error);
                broadcastIntent.putExtra("error", error);
            }
            else {
                // Get the type of transition (entry or exit)
                int transitionType = geofencingEvent.getGeofenceTransition();

                List<Geofence> triggerList = geofencingEvent.getTriggeringGeofences();
                List<GeoNotification> geoNotifications = new ArrayList<>();
                for (Geofence fence : triggerList) {
                    String fenceId = fence.getRequestId();
                    GeoNotification geoNotification = store
                            .getGeoNotification(fenceId);

                    if (geoNotification != null && !GeofencePlugin.isSnoozed(geoNotification.id) && geoNotification.isWithinTimeRange()) {
                        geoNotification.transitionType = transitionType;
                        geoNotifications.add(geoNotification);
                    }
                }

                if (geofencingEvent.getTriggeringLocation() != null) {
                    broadcastIntent.putExtra("triggerLocation", geofencingEvent.getTriggeringLocation());
                }

                if (transitionType == Geofence.GEOFENCE_TRANSITION_ENTER
                        || transitionType == Geofence.GEOFENCE_TRANSITION_EXIT) {
                    logger.log(Log.DEBUG, "Geofence transition detected");

                    if (geoNotifications.size() > 0) {
                        for (GeoNotification geoNotification : geoNotifications) {
                            if (geoNotification.notification != null && geoNotification.notification.canBeTriggered()) {
                                this.updateLastTriggeredByNotificationId(geoNotification.notification.id, geoNotifications);
                                notifier.notify(geoNotification.notification,
                                        transitionType == Geofence.GEOFENCE_TRANSITION_ENTER ? "enter" : "exit");
                                logger.log(Log.DEBUG, "Notification sent");
                            } else {
                                logger.log(Log.DEBUG, "Frequency control. Skip notification");
                            }
                        }

                        broadcastIntent.putExtra("transitionData", Gson.get().toJson(geoNotifications));
                        GeofencePlugin.onTransitionReceived(geoNotifications);
                    }
                }
                else if (transitionType == Geofence.GEOFENCE_TRANSITION_DWELL) {
                    logger.log(Log.DEBUG, "Geofence transition dwell detected");

                    if (geoNotifications.size() > 0) {
                        broadcastIntent.putExtra("transitionData", Gson.get().toJson(geoNotifications));
                        GeofencePlugin.onTransitionReceived(geoNotifications);
                    }
                }
                else {
                    String error = "Geofence transition error: " + transitionType;
                    logger.log(Log.ERROR, error);
                    broadcastIntent.putExtra("error", error);
                }

                broadcastIntent.putExtra("trigger_listSize", triggerList.size());
                broadcastIntent.putExtra("trigger_transitionType", transitionType);

                for (GeoNotification geoNotification : geoNotifications) {
                    if (geoNotification.url != null) {
                        String transition = null;
                        if (transitionType == Geofence.GEOFENCE_TRANSITION_ENTER)
                            transition = "ENTER";
                        if (transitionType == Geofence.GEOFENCE_TRANSITION_DWELL)
                            transition = "DWELL";
                        if (transitionType == Geofence.GEOFENCE_TRANSITION_EXIT)
                            transition = "EXIT";

                        PersistableBundle bundle = new PersistableBundle();
                        bundle.putString("id", geoNotification.id);
                        bundle.putString("url", geoNotification.url);                       
					    bundle.putString("applicationId",geoNotification.applicationId); // extra field
                        bundle.putString("javascriptId", geoNotification.javascriptId); // extra field
                        bundle.putString("sessionToken", geoNotification.sessionToken); // extra field
                        bundle.putString("userId", geoNotification.userId); // extra field
                        bundle.putString("authorization", geoNotification.authorization);
                        bundle.putString("transition", transition);
                        /*
                        if (geoNotification.notification != null && geoNotification.notification.data != null) {
                            //resultIntent.putExtra("geofence.notification.data", geoNotification.notification.getDataJson());
                            bundle.putString("data", geoNotification.notification.data);
                        }
                        */

                        TimeZone tz = TimeZone.getTimeZone("UTC");
                        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                        df.setTimeZone(tz);
                        bundle.putString("date", df.format(new Date()));

                        Log.i(GeofencePlugin.TAG, "Scheduling job for " + geoNotification.toJson());

                        JobScheduler jobScheduler =
                                (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
                        jobScheduler.schedule(
                                new JobInfo.Builder(1, new ComponentName(context, TransitionJobService.class))
                                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
										.setMinimumLatency(1)
										.setOverrideDeadline(1)
                                        .setExtras(bundle)
                                        .build()
                        );
                    }
                }
            }
        }
        catch (Exception e) {
            logger.log(e.getMessage(), e);

            String errMsg = e.getMessage();
            if (e.getStackTrace().length > 0) {
                StackTraceElement stackTrace_line1 = e.getStackTrace()[0];
                String stackStr = String.format("Class: %s, Line: %s", stackTrace_line1.getClassName(), stackTrace_line1.getLineNumber());
                errMsg = String.format("%s\nStack - %s", errMsg, stackStr);
            }

            broadcastIntent.putExtra("error", errMsg);
        }

        context.sendBroadcast(broadcastIntent);

    }

	/*
	public void onReceive(Context context, Intent intent) {
        beepHelper = new BeepHelper();
        store = new GeoNotificationStore(context);
        Logger.setLogger(new Logger(GeofencePlugin.TAG, context, false));
        Logger logger = Logger.getLogger();
        logger.log(Log.DEBUG, "ReceiveTransitionsIntentService - onHandleIntent");
        //Intent broadcastIntent = new Intent(GeofenceTransitionIntent);
        notifier = new GeoNotificationNotifier(
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE),
                context
        );

        // TODO: refactor this, too long
        // First check for errors
        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);
        if (geofencingEvent.hasError()) {
            // Get the error code with a static method
            int errorCode = geofencingEvent.getErrorCode();
            String error = "Location Services error: " + Integer.toString(errorCode);
            // Log the error
            logger.log(Log.ERROR, error);
            //broadcastIntent.putExtra("error", error);
        } else {
            // Get the type of transition (entry or exit)
            int transitionType = geofencingEvent.getGeofenceTransition();

            List<Geofence> triggerList = geofencingEvent.getTriggeringGeofences();
            List<GeoNotification> geoNotifications = new ArrayList<>();
            for (Geofence fence : triggerList) {
                String fenceId = fence.getRequestId();
                GeoNotification geoNotification = store
                        .getGeoNotification(fenceId);

                if (geoNotification != null && !GeofencePlugin.isSnoozed(geoNotification.id) && geoNotification.isWithinTimeRange()) {
                    geoNotification.transitionType = transitionType;
                    geoNotifications.add(geoNotification);
                }
            }

            if (transitionType == Geofence.GEOFENCE_TRANSITION_ENTER
                    || transitionType == Geofence.GEOFENCE_TRANSITION_EXIT) {
                logger.log(Log.DEBUG, "Geofence transition detected");

                if (geoNotifications.size() > 0) {
                    for (GeoNotification geoNotification : geoNotifications) {
                        if (geoNotification.notification != null && geoNotification.notification.canBeTriggered()) {
                            this.updateLastTriggeredByNotificationId(geoNotification.notification.id, geoNotifications);
                            notifier.notify(geoNotification.notification,
                                    transitionType == Geofence.GEOFENCE_TRANSITION_ENTER ? "enter" : "exit");
                            logger.log(Log.DEBUG, "Notification sent");
                        } else {
                            logger.log(Log.DEBUG, "Frequency control. Skip notification");
                        }
                    }

                    //broadcastIntent.putExtra("transitionData", Gson.get().toJson(geoNotifications));
                    GeofencePlugin.onTransitionReceived(geoNotifications);
                }
            } else if (transitionType == Geofence.GEOFENCE_TRANSITION_DWELL) {
                logger.log(Log.DEBUG, "Geofence transition dwell detected");

                if (geoNotifications.size() > 0) {
                    //broadcastIntent.putExtra("transitionData", Gson.get().toJson(geoNotifications));
                    GeofencePlugin.onTransitionReceived(geoNotifications);
                }
            } else {
                String error = "Geofence transition error: " + transitionType;
                logger.log(Log.ERROR, error);
                //broadcastIntent.putExtra("error", error);
            }


            //sendBroadcast(broadcastIntent);

            for (GeoNotification geoNotification : geoNotifications) {
                if (geoNotification.url != null) {
                    String transition = null;
                    if (transitionType == Geofence.GEOFENCE_TRANSITION_ENTER)
                        transition = "ENTER";
                    if (transitionType == Geofence.GEOFENCE_TRANSITION_DWELL)
                        transition = "DWELL";
                    if (transitionType == Geofence.GEOFENCE_TRANSITION_EXIT)
                        transition = "EXIT";

                    PersistableBundle bundle = new PersistableBundle();
                    bundle.putString("id", geoNotification.id);
                    bundle.putString("url", geoNotification.url);
                    bundle.putString("authorization", geoNotification.authorization);
                    bundle.putString("transition", transition);

					TimeZone tz = TimeZone.getTimeZone("UTC");
					DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
					df.setTimeZone(tz);
                    bundle.putString("date", df.format(new Date()));

                    Log.i(GeofencePlugin.TAG, "Scheduling job for " + geoNotification.toJson());

                    JobScheduler jobScheduler =
                            (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
                    jobScheduler.schedule(
                            new JobInfo.Builder(1, new ComponentName(context, TransitionJobService.class))
                                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
									.setMinimumLatency(1)
                                    .setOverrideDeadline(1)
                                    .setExtras(bundle)
                                    .build()
                    );
                }
            }
        }

    }
	*/
	
    private void updateLastTriggeredByNotificationId(int id, List<GeoNotification> geoList) {
        if (geoList != null) {
            for (GeoNotification geo : geoList) {
                if (geo.notification != null && geo.notification.id == id) {
                    geo.notification.setLastTriggered();
                    store.setGeoNotification(geo);
                }
            }
        }
    }

}
