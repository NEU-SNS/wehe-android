package mobi.meddle.wehe.bean;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.telephony.TelephonyManager;
import android.util.Log;

public class DeviceInfoBean {

    //public String deviceId;
    //public String user;
    public String manufacturer;
    public String model;
    public String os;
    //public String phoneType;
    public String carrierName;
    public String networkType;

    public String cellInfo;
    public Location location;

    public DeviceInfoBean(Context context) {
        String[] NETWORK_TYPES = {"UNKNOWN", // 0 -
                // NETWORK_TYPE_UNKNOWN
                "GPRS", // 1 - NETWORK_TYPE_GPRS
                "EDGE", // 2 - NETWORK_TYPE_EDGE
                "UMTS", // 3 - NETWORK_TYPE_UMTS
                "CDMA", // 4 - NETWORK_TYPE_CDMA
                "EVDO_0", // 5 - NETWORK_TYPE_EVDO_0
                "EVDO_A", // 6 - NETWORK_TYPE_EVDO_A
                "1xRTT", // 7 - NETWORK_TYPE_1xRTT
                "HSDPA", // 8 - NETWORK_TYPE_HSDPA
                "HSUPA", // 9 - NETWORK_TYPE_HSUPA
                "HSPA", // 10 - NETWORK_TYPE_HSPA
                "IDEN", // 11 - NETWORK_TYPE_IDEN
                "EVDO_B", // 12 - NETWORK_TYPE_EVDO_B
                "LTE", // 13 - NETWORK_TYPE_LTE
                "EHRPD", // 14 - NETWORK_TYPE_EHRPD
                "HSPAP", // 15 - NETWORK_TYPE_HSPAP
        };

        TelephonyManager telephonyManager;
        ConnectivityManager connectivityManager;
        LocationManager locationManager = null;
        String locationProviderName = "NoPermission";
        Criteria criteriaCoarse;

        telephonyManager = (TelephonyManager) context
                .getSystemService(Context.TELEPHONY_SERVICE);

        // initialize connectivity manager
        connectivityManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);


        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            // initialize location manager
            locationManager = (LocationManager) context
                    .getSystemService(Context.LOCATION_SERVICE);
            criteriaCoarse = new Criteria();
            /*
             * "Coarse" accuracy means "no need to use GPS". Typically a gShots
             * phone would be located in a building, and GPS may not be able to
             * acquire a location. We only care nav_about the location to determine the
             * country, so we don't need a super accurate location, cell/wifi is
             * good enough.
             */
            criteriaCoarse.setAccuracy(Criteria.ACCURACY_COARSE);
            criteriaCoarse.setPowerRequirement(Criteria.POWER_LOW);
            locationProviderName = locationManager.getBestProvider(
                    criteriaCoarse, true);
            Log.d("GetLocation", "Location provider: " + locationProviderName);
        }

        // deviceInfoBean.deviceId = getDeviceId();
        this.manufacturer = Build.MANUFACTURER;
        this.model = Build.MODEL;
        this.os = String.format("INCREMENTAL:%s, RELEASE:%s, SDK_INT:%s",
                Build.VERSION.INCREMENTAL, Build.VERSION.RELEASE,
                Build.VERSION.SDK_INT);
        // deviceInfoBean.user = Build.VERSION.CODENAME;

        // get phone type
        /*
         * switch (telephonyManager.getPhoneType()) {
         *
         * case TelephonyManager.PHONE_TYPE_SIP: deviceInfoBean.phoneType =
         * "SIP"; case TelephonyManager.PHONE_TYPE_CDMA:
         * deviceInfoBean.phoneType = "CDMA"; case
         * TelephonyManager.PHONE_TYPE_GSM: deviceInfoBean.phoneType = "GSM";
         * case TelephonyManager.PHONE_TYPE_NONE: deviceInfoBean.phoneType =
         * "NONE"; default: deviceInfoBean.phoneType = "UNKNOWN"; }
         */

        // get network operator name
        this.carrierName = telephonyManager.getNetworkOperatorName();

        // get network type
        NetworkInfo networkInfo = connectivityManager
                .getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (networkInfo != null
                && networkInfo.getState() == NetworkInfo.State.CONNECTED) {
            this.networkType = "WIFI";
        } else {
            int typeIndex = telephonyManager.getNetworkType();
            if (typeIndex < NETWORK_TYPES.length)
                this.networkType = NETWORK_TYPES[typeIndex];
            else
                this.networkType = "Unrecognized: " + typeIndex;
        }

        this.cellInfo = "FAILED";

        // get location
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED && locationProviderName != null) {
            try {
                Log.d("GetLocation", "Provider " + locationProviderName);
                Location location = null;
                if (locationManager != null) {
                    location = locationManager
                            .getLastKnownLocation(locationProviderName);
                } else {
                    Log.d("Testing", "LocationManager is null");
                }
                if (location == null) {
                    Log.w("GetLocation", "Cannot obtain location from provider "
                            + locationProviderName);
                    this.location = new Location("unknown");
                } else {
                    Log.d("GetLocation", "Was able to get location");
                    this.location = location;
                }
            } catch (IllegalArgumentException e) {
                Log.e("GetLocation", "Cannot obtain location", e);
                this.location = new Location("unknown");
            }
        } else {
            Log.d("Location", "We don't have location, Just proceed without it");
            this.location = new Location("unknown");
        }
    }
}
