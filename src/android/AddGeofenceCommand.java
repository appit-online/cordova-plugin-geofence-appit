package com.appit.cordova.geofence;

import android.app.PendingIntent;
import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofenceStatusCodes;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import org.apache.cordova.LOG;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AddGeofenceCommand extends AbstractGoogleServiceCommand {
	// https://developers.google.com/android/reference/com/google/android/gms/location/GeofencingRequest.Builder#public-geofencingrequest.builder-setinitialtrigger-int-initialtrigger   
	public static final int INITIAL_TRIGGER_NONE = 0;
	
    private List<Geofence> geofencesToAdd;
    private PendingIntent pendingIntent;
	private int initialTrigger;

/*
    public AddGeofenceCommand(Context context, PendingIntent pendingIntent,
                              List<Geofence> geofencesToAdd) {
        super(context);
        this.geofencesToAdd = geofencesToAdd;
        this.pendingIntent = pendingIntent;
    }
	*/
	public AddGeofenceCommand(Context context, PendingIntent pendingIntent,
                              List<Geofence> geofencesToAdd) {
        this(context, pendingIntent, geofencesToAdd, GeofencingRequest.INITIAL_TRIGGER_ENTER);
    }

    public AddGeofenceCommand(Context context, PendingIntent pendingIntent,
                              List<Geofence> geofencesToAdd,
                              int initialTrigger) {
        super(context);
        this.geofencesToAdd = geofencesToAdd;
        this.pendingIntent = pendingIntent;
        this.initialTrigger = initialTrigger;
    }

    @Override
    public void ExecuteCustomCode() {
        logger.log(Log.DEBUG, "Adding new geofences...");
        if (geofencesToAdd != null && geofencesToAdd.size() > 0) {
            try {
                GeofencingRequest.Builder requestBuilder = new GeofencingRequest.Builder();
                requestBuilder.setInitialTrigger(this.initialTrigger);
                requestBuilder.addGeofences(geofencesToAdd);

                GeofencingClient geofencingClient = LocationServices.getGeofencingClient(this.context);
                geofencingClient.addGeofences(requestBuilder.build(), pendingIntent)
                        .addOnSuccessListener(new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void aVoid) {
                                logger.log(Log.DEBUG, "Geofences successfully added with geofencingClient");
                                CommandExecuted();
                            }
                        })
                        .addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                try {
                                    Map<Integer, String> errorCodeMap = new HashMap<Integer, String>();
                                    errorCodeMap.put(GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE, GeofencePlugin.ERROR_GEOFENCE_NOT_AVAILABLE);
                                    errorCodeMap.put(GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES, GeofencePlugin.ERROR_GEOFENCE_LIMIT_EXCEEDED);

									String errMsg = e.getMessage();
									if (e.getStackTrace().length > 0) {
										StackTraceElement stackTrace_line1 = e.getStackTrace()[0];
										String stackStr = String.format("Class: %s, Line: %s", stackTrace_line1.getClassName(), stackTrace_line1.getLineNumber());
										errMsg = String.format("%s\nStack - %s", errMsg, stackStr);
									}
			
                                    //Integer statusCode = status.getStatusCode();
                                    String message = "Adding geofences failed - Exception.Message: " + errMsg;
									// String message = "Adding geofences failed - Exception.Message: " + e.getMessage();
                                    JSONObject error = new JSONObject();
                                    error.put("message", message);
									//error.put("status", Status.getStatusCode());

    //                                if (statusCode == GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE) {
    //                                    error.put("code", GeofencePlugin.ERROR_GEOFENCE_NOT_AVAILABLE);
    //                                } else if (statusCode == GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES) {
    //                                    error.put("code", GeofencePlugin.ERROR_GEOFENCE_LIMIT_EXCEEDED);
    //                                } else {
    //                                    error.put("code", GeofencePlugin.ERROR_UNKNOWN);
    //                                }

                                    logger.log(Log.ERROR, message);
                                    CommandExecuted(error);
                                } catch (JSONException exception) {
                                    CommandExecuted(exception);
                                }
                            }
                        });
            } catch (Exception exception) {
                logger.log(LOG.ERROR, "Exception while adding geofences");
                exception.printStackTrace();
                CommandExecuted(exception);
            }
        } else {
            logger.log(Log.DEBUG, "Nothing to add");
            CommandExecuted();
        }
    }
}
