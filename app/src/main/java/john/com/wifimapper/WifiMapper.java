package john.com.wifimapper;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v7.app.NotificationCompat;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.List;

public class WifiMapper
        extends Service
{
    private static final String TAG = WifiMapper.class.getSimpleName();
    private static final int WIFI_MAPPER_NOTIFICATION_ID = 101;

    WifiManager wifiManager;
    BroadcastReceiver scanReceiver;
    FirebaseDatabase database;


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
        wifiManager.startScan();
        return super.onStartCommand(intent, flags, startId);
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
            Log.d(TAG, "network: " + r);
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
    }

    @Override
    public void onDestroy()
    {
        Log.d(TAG, "onDestroy");
        unregisterReceiver(scanReceiver);
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
}
