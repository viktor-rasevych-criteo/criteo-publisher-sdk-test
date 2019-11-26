package com.criteo.publisher.Util;

import static android.content.ContentValues.TAG;

import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import com.criteo.publisher.DependencyProvider;
import com.criteo.publisher.model.AdSize;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Locale;

public final class DeviceUtil {

    private static final String CRITEO_LOGGING = "CRITEO_LOGGING";
    private static final String DEVICE_ID_LIMITED = "00000000-0000-0000-0000-000000000000";

    private static AdSize sizePortrait = new AdSize(0, 0);
    private static AdSize sizeLandscape = new AdSize(0, 0);

    private DeviceUtil() {
    }

    public static void setScreenSize(int screenWidth, int screenHeight) {
        sizePortrait = new AdSize(screenWidth, screenHeight);
        sizeLandscape = new AdSize(screenHeight, screenWidth);
    }

    public static AdSize getSizePortrait() {
        return sizePortrait;
    }

    public static AdSize getSizeLandscape() {
        return sizeLandscape;
    }

    public static String getDeviceModel() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        if (model.toLowerCase(Locale.ROOT).startsWith(manufacturer.toLowerCase(Locale.ROOT))) {
            return model;
        } else {
            return manufacturer + " " + model;
        }
    }

    public static String getAdvertisingId(@NonNull Context context, @NonNull AdvertisingInfo advertisingInfo) {
        try {
            if (advertisingInfo.isLimitAdTrackingEnabled(context)) {
                return DEVICE_ID_LIMITED;
            }
            return advertisingInfo.getAdvertisingId(context);
        } catch (Exception e) {
            // FIXME This seems like a dead code, because AdvertisingInfo already catch exceptions
            Log.e("DeviceUtil", "Error trying to get Advertising id: " + e.getMessage());
        }
        return null;
    }

    public static int isLimitAdTrackingEnabled(Context context) {
        // FIXME This entire method seems dumb. It's just a mapping from bool to 0,1

        AdvertisingInfo advertisingInfo = DependencyProvider.getInstance().provideAdvertisingInfo();

        try {
            return advertisingInfo.isLimitAdTrackingEnabled(context) ? 1 : 0;
        } catch (Exception e) {
            // FIXME This seems like a dead code, because AdvertisingInfo already catch exceptions
            Log.e("DeviceUtil", "Error trying to check limited ad tracking: " + e.getMessage());
        }
        return 0;
    }

    public static String createDfpCompatibleString(String stringToEncode) {

        if (TextUtils.isEmpty(stringToEncode)) {
            return null;
        }

        try {
            byte[] byteUrl = stringToEncode.getBytes(Charset.forName("UTF-8"));
            String base64Url = Base64.encodeToString(byteUrl, Base64.NO_WRAP);
            String utf8 = Charset.forName("UTF-8").name();
            return URLEncoder.encode(URLEncoder.encode(base64Url, utf8), utf8);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static boolean isVersionSupported() {
        if (android.os.Build.VERSION.SDK_INT < 19) {
            Log.e(TAG, "Unsupported Android version");
            return false;
        }

        return true;
    }

    public static boolean isLoggingEnabled() {
        String log = System.getenv(CRITEO_LOGGING);
        return TextUtils.isEmpty(log) ? false : Boolean.parseBoolean(log);
    }

}
