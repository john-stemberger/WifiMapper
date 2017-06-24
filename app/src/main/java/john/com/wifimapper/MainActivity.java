package john.com.wifimapper;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.List;

public class MainActivity
        extends AppCompatActivity
        implements View.OnClickListener
{
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int PERMISSION_REQUEST_CODE = 1;

    WifiManager wifi;
    ListView lv;
    TextView textStatus;
    Button buttonScan;
    int size = 0;
    List<ScanResult> results;
    BroadcastReceiver scanReceiver = null;

    FirebaseDatabase database;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textStatus = (TextView) findViewById(R.id.textStatus);
        buttonScan = (Button) findViewById(R.id.buttonScan);
        buttonScan.setOnClickListener(this);

        database = FirebaseDatabase.getInstance();
        database.setPersistenceEnabled(true);

        wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifi.isWifiEnabled() == false)
        {
            Toast.makeText(getApplicationContext(), "wifi is disabled..making it enabled", Toast.LENGTH_LONG).show();
            wifi.setWifiEnabled(true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults)
    {
        Log.e("JOHN", "onRequestPermissionsResult " + permissions);
        for (String s : permissions)
        {
            Log.e("JOHN", "    " + s);
        }
        switch (requestCode)
        {
            case PERMISSION_REQUEST_CODE:
            {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length <= 1)
                {
                    return; // canceled by user
                }
                for (int i = 0; i < grantResults.length; i++)
                {
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED)
                    {
                        return;
                    }
                }
                startWifiNetworkMonitoring();
            }
        }
    }

    private void startWifiNetworkMonitoring()
    {
        scanReceiver = new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context c, Intent intent)
            {
                onResults(c, intent);
            }
        };
        Log.e("JOHN", "startWifiNetworkMonitoring");
        registerReceiver(scanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        wifi.startScan();
    }

    private void stopWifiNetworkMonitoring()
    {
        if (scanReceiver != null)
        {
            unregisterReceiver(scanReceiver);
            scanReceiver = null;
        }
    }

    @Override
    protected void onResume()
    {
        Log.e("JOHN", "onResume");
        super.onResume();
        checkForPermissions();
    }

    private void checkForPermissions()
    {
        List<String> permissionsList = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            permissionsList.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED)
        {
            permissionsList.add(Manifest.permission.ACCESS_WIFI_STATE);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CHANGE_WIFI_STATE) != PackageManager.PERMISSION_GRANTED)
        {
            permissionsList.add(Manifest.permission.CHANGE_WIFI_STATE);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            permissionsList.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }

        if (permissionsList.size() > 0)
        {
            ActivityCompat.requestPermissions(this, permissionsList.toArray(new String[permissionsList.size()]),
                    PERMISSION_REQUEST_CODE);
        }
        else
        {
            startWifiNetworkMonitoring();
        }
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        stopWifiNetworkMonitoring();
    }

    private void onResults(Context context, Intent intent)
    {
        results = wifi.getScanResults();
        if (results == null)
        {
            return;
        }
        for (ScanResult r : results)
        {
            Log.e("JOHN", "onResults " + r);
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

    private void saveResult(ScanResult result)
    {
        if (TextUtils.isEmpty(result.SSID))
        {
            return;
        }
        String sid = result.SSID;
        sid = cleanNameForDb(sid);
        textStatus.setText(textStatus.getText() + "\n" + result.SSID);
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

    /**
     * View.OnClickListener
     **/
    public void onClick(View view)
    {
        wifi.startScan();
        textStatus.setText("");
    }

}
