package io.cloudthing.cloudthingbutton;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Created by patryk on 14.12.17.
 */

public class BluetoothActivity extends Activity {

    private TextView logs;
    private Intent intent;
    private MyResultReceiver resultReceiver;

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopService(intent);
    }

    class UpdateUI implements Runnable
    {
        String updateString;

        public UpdateUI(String updateString) {
            this.updateString = updateString;
        }
        public void run() {
            logs.append(updateString + "\n");
        }
    }

    class MyResultReceiver extends ResultReceiver
    {
        public MyResultReceiver(Handler handler) {
            super(handler);
            System.out.println("MyResultReceiver");
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            if (resultCode == 10) {
                final Object log = resultData.get("log");
                if (log != null) {
                    runOnUiThread(new UpdateUI(log.toString()));
                }
            }
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout lView = new LinearLayout(this);


        logs = new TextView(this);
        logs.setText("Logs:\n");
        logs.setVerticalScrollBarEnabled(true);
        logs.setMovementMethod(new ScrollingMovementMethod());
        logs.setGravity(Gravity.BOTTOM);

        resultReceiver = new MyResultReceiver(null);
        intent = new Intent(this, LertaMeterService.class);
        intent.putExtra("receiver", resultReceiver);
        startService(intent);

        lView.addView(logs);
        setContentView(lView);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                break;
            default:
                break;
        }
        return true;
    }

}
