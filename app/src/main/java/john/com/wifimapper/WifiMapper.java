package john.com.wifimapper;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.util.List;

public class WifiMapper
        extends Service
{
    private static final String TAG = "JOHN";//WifiMapper.class.getSimpleName();

    WifiManager wifiManager;
    BroadcastReceiver scanReceiver;

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
//            saveResult(r);
        }
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onDestroy()
    {
        unregisterReceiver(scanReceiver);
        scanReceiver = null;
        wifiManager = null;
        super.onDestroy();
    }

}
