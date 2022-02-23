package cn.eciot.ble_demo_java;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Date;
import java.util.regex.Pattern;
import java.text.SimpleDateFormat;

public class DeviceActivity extends AppCompatActivity {

    ScrollView scrollView = null;
    TextView receiveDataTextView = null;
    CheckBox scrollCheckBox = null;
    CheckBox hexRevCheckBox = null;
    CheckBox hexSendCheckBox = null;
    EditText sendDataEditText = null;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device);

        scrollView = findViewById(R.id.sv_receive);
        receiveDataTextView = findViewById(R.id.tv_receive_data);
        scrollCheckBox = findViewById(R.id.cb_scroll);
        hexRevCheckBox = findViewById(R.id.cb_hex_rev);
        hexSendCheckBox = findViewById(R.id.cb_hex_send);
        sendDataEditText = findViewById(R.id.et_send);
        findViewById(R.id.iv_back).setOnClickListener((View view)->{
            ECBLE.offBLEConnectionStateChange();
            ECBLE.closeBLEConnection();
            finish();
        });
        findViewById(R.id.bt_send).setOnClickListener((View view)->{
            if (sendDataEditText.getText().toString().isEmpty()) {
                return;
            }
            if (hexSendCheckBox.isChecked()) {
                //send hex
                if (!Pattern.compile("^[0-9a-fA-F]+$").matcher(sendDataEditText.getText().toString()).matches()) {
                    showAlert("warn", "Format error. Can only be 0-9、a-f、A-F.",()->{});
                    return;
                }
                if (sendDataEditText.getText().toString().length() % 2 == 1) {
                    showAlert("warn", "Format error. The length can only be even.",()->{});
                    return;
                }
                ECBLE.easySendData(sendDataEditText.getText().toString(), true);
            } else {
                //send string
                ECBLE.easySendData(sendDataEditText.getText().toString().replace("\n","\r\n"), false);
            }
        });
        findViewById(R.id.bt_clear).setOnClickListener((View view2)->{
            receiveDataTextView.setText("");
        });

        ECBLE.onBLEConnectionStateChange((boolean ok)->{
            showToast("Disconnect");
        });
        ECBLE.onBLECharacteristicValueChange((String hex,String string)->{
            runOnUiThread(()->{
                @SuppressLint("SimpleDateFormat") String timeStr = new SimpleDateFormat("[HH:mm:ss,SSS]:").format(new Date(System.currentTimeMillis()));
                String nowStr = receiveDataTextView.getText().toString();
                if (hexRevCheckBox.isChecked()) {
                    receiveDataTextView.setText(nowStr + timeStr + hex + "\r\n");
                } else {
                    receiveDataTextView.setText(nowStr + timeStr + string + "\r\n");
                }
                if (scrollCheckBox.isChecked()) {
                    scrollView.post(()->{
                        scrollView.fullScroll(ScrollView.FOCUS_DOWN);
                    });
                }
            });
        });
    }

    public void showToast(String text){
        runOnUiThread(()->{
            Toast.makeText(this,text,Toast.LENGTH_SHORT).show();
        });
    }

    public void showAlert(String title,String content,Runnable callback){
        runOnUiThread(()->{
            new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage(content)
                    .setPositiveButton("OK",  (dialogInterface , i)->{
                        new Thread(callback).start();
                    })
                    .setCancelable(false)
                    .create().show();
        });
    }
}
