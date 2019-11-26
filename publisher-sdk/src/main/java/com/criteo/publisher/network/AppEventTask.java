package com.criteo.publisher.network;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import com.criteo.publisher.DependencyProvider;
import com.criteo.publisher.Util.AdvertisingInfo;
import com.criteo.publisher.Util.AppEventResponseListener;
import com.criteo.publisher.Util.DeviceUtil;
import org.json.JSONObject;

public class AppEventTask extends AsyncTask<Object, Void, JSONObject> {

    private static final String TAG = "Criteo.AET";
    private static final int SENDER_ID = 2379;
    protected static final String THROTTLE = "throttleSec";
    private final Context mContext;
    private final AppEventResponseListener responseListener;
    private final AdvertisingInfo advertisingInfo;

    public AppEventTask(
        Context context,
        AppEventResponseListener responseListener,
        AdvertisingInfo advertisingInfo
    ) {
        this.mContext = context;
        this.responseListener = responseListener;
        this.advertisingInfo = advertisingInfo;
    }

    @Override
    protected JSONObject doInBackground(Object... objects) {
        JSONObject jsonObject = null;

        try {
            jsonObject = doAppEventTask(objects);
        } catch (Throwable tr) {
            Log.e(TAG, "Internal AET exec error.", tr);
        }

        return jsonObject;
    }

    private JSONObject doAppEventTask(Object[] objects) {
        String eventType = (String) objects[0];
        int limitedAdTracking = DeviceUtil.isLimitAdTrackingEnabled(mContext);
        String gaid = DeviceUtil.getAdvertisingId(mContext, advertisingInfo);
        String appId = mContext.getApplicationContext().getPackageName();

        PubSdkApi pubSdkApi = DependencyProvider.getInstance().providePubSdkApi();
        JSONObject response = pubSdkApi.postAppEvent(mContext, SENDER_ID, appId, gaid, eventType, limitedAdTracking);
        if (response != null) {
            Log.d(TAG, response.toString());
        }
        return response;
    }

    @Override
    protected void onPostExecute(JSONObject result) {
        try {
            doOnPostExecute(result);
        } catch (Throwable tr) {
            Log.e(TAG, "Internal AET PostExec error.", tr);
        }
    }

    private void doOnPostExecute(JSONObject result) {
        super.onPostExecute(result);
        if (responseListener != null) {
            if (result != null && result.has(THROTTLE)) {
                responseListener.setThrottle(result.optInt(THROTTLE, 0));
            } else {
                responseListener.setThrottle(0);
            }
        }
    }
}
