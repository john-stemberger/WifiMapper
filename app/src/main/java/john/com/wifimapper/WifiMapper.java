package john.com.wifimapper;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.NotificationCompat;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.List;

public class WifiMapper
        extends Service
        implements LocationListener
{
    private static final String TAG = WifiMapper.class.getSimpleName();
    private static final int WIFI_MAPPER_NOTIFICATION_ID = 101;
    private static final long UPDATE_INTERVAL_IN_MILLISECONDS = 10000;
    private static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = UPDATE_INTERVAL_IN_MILLISECONDS / 2;

    WifiManager wifiManager;
    BroadcastReceiver scanReceiver;
    FirebaseDatabase database;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationRequest gpsLocationRequest;
    private LocationCallback mLocationCallback;
    private Location mCurrentLocation;


    public WifiMapper()
    {
    }

    @Override
    public void onCreate()
    {
        Log.d(TAG, "onCreate");
        super.onCreate();
        initWifiManager();
        initScanReceiver();
        initFirebase();
        initLocationMonitoring();
    }

    private void initWifiManager()
    {
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager.isWifiEnabled() == false)
        {
            Toast.makeText(getApplicationContext(), "wifi is disabled..making it enabled", Toast.LENGTH_LONG).show();
            wifiManager.setWifiEnabled(true);
        }
    }

    private void initScanReceiver()
    {
        scanReceiver = new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context c, Intent intent)
            {
                onScanResult(c, intent);
            }
        };
        registerReceiver(scanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
    }

    private void initFirebase()
    {
        database = FirebaseDatabase.getInstance();
        database.setPersistenceEnabled(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        Log.d(TAG, "onStartCommand");

        setupForegroundNotification();
        wifiManager.startScan();

        return super.onStartCommand(intent, flags, startId);
    }

    private void initLocationMonitoring()
    {
        gpsLocationRequest = new LocationRequest();
        gpsLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);
        gpsLocationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);
        gpsLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        mLocationCallback = new LocationCallback()
        {
            @Override
            public void onLocationResult(LocationResult locationResult)
            {
                super.onLocationResult(locationResult);
                mCurrentLocation = locationResult.getLastLocation();
                Log.e(TAG, "lastLocation: " + mCurrentLocation);
            }
        };

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            Log.e(TAG, "unable to get location updates without permission");
            return;
        }
        fusedLocationClient.requestLocationUpdates(gpsLocationRequest, mLocationCallback, Looper.myLooper());
    }

    private void setupForegroundNotification()
    {
        Intent notificationIntent = new Intent(getApplicationContext(), MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        Notification notification = new NotificationCompat.Builder(this)
                .setContentTitle("Wifi mapper in progress")
                .setContentText("Last Broadcast: ")
                .setSmallIcon(android.R.drawable.ic_menu_add)
                .setContentIntent(pendingIntent)
                .build();
        startForeground(WIFI_MAPPER_NOTIFICATION_ID, notification);

    }

    private void onScanResult(Context context, Intent intent)
    {
        Log.d(TAG, "onCreate");
        List<ScanResult> results = wifiManager.getScanResults();
        if (results == null)
        {
            return;
        }
        for (ScanResult r : results)
        {
            Log.d(TAG, mCurrentLocation + "network: " + r);
            saveResult(r);
        }
    }

    private String cleanNameForDb(String sid)
    {
        sid = sid.replaceAll("\\.", "_");
        sid = sid.replaceAll("\\$", "_");
        sid = sid.replaceAll("#", "_");
        sid = sid.replaceAll("\\[", "_");
        sid = sid.replaceAll("]", "_");
        return sid;

    }

    private boolean isSecure(@NonNull ScanResult network)
    {
        String capabilities = network.capabilities;
        Log.w(TAG, network.SSID + " capabilities : " + capabilities);
        if (capabilities.toUpperCase().contains("WEP"))
        {
            // WEP Network
            return true;
        }
        else if (capabilities.toUpperCase().contains("WPA") ||
                capabilities.toUpperCase().contains("WPA2"))
        {
            // WPA or WPA2 Network
            return true;
        }
        else
        {
            // Open Network
            return false;
        }
    }

    private void saveResult(ScanResult result)
    {
        if (TextUtils.isEmpty(result.SSID))
        {
            return;
        }
        String sid = result.SSID;
        sid = cleanNameForDb(sid);
        DatabaseReference db = database.getReference();
        DatabaseReference row = db.child("networks").child(sid);
        DatabaseReference accessPoint = row.child(result.BSSID);
        accessPoint.child("BSSID").setValue(result.BSSID);
        accessPoint.child("SID").setValue(result.SSID);
        accessPoint.child("capabilities").setValue(result.capabilities);
        accessPoint.child("level").setValue(result.level);
        accessPoint.child("frequency").setValue(result.frequency);
        accessPoint.child("timestamp").setValue(result.timestamp);
        accessPoint.child("toString").setValue("" + result);
        accessPoint.child("lastLocation").setValue(mCurrentLocation);
    }

    @Override
    public void onDestroy()
    {
        Log.d(TAG, "onDestroy");
        unregisterReceiver(scanReceiver);
        fusedLocationClient.removeLocationUpdates(mLocationCallback);
        fusedLocationClient = null;
        mLocationCallback = null;
        gpsLocationRequest = null;
        scanReceiver = null;
        wifiManager = null;
        database = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * LocationListener
     */

    @Override
    public void onLocationChanged(Location location)
    {

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras)
    {

    }

    @Override
    public void onProviderEnabled(String provider)
    {

    }

    @Override
    public void onProviderDisabled(String provider)
    {

    }


}
