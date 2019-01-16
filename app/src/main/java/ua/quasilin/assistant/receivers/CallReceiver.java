package ua.quasilin.assistant.receivers;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.RequiresPermission;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import ua.quasilin.assistant.R;
import ua.quasilin.assistant.utils.ApplicationParameters;
import ua.quasilin.assistant.utils.CustomAuthenticator;
import ua.quasilin.assistant.utils.DisplayState;
import ua.quasilin.assistant.utils.HistoryArchive;
import ua.quasilin.assistant.utils.HistoryType;
import ua.quasilin.assistant.utils.connection.IConnector;
import ua.quasilin.assistant.utils.Notificator;

/**
 * Created by szpt_user045 on 29.10.2018.
 */

public class CallReceiver extends BroadcastReceiver {

    private static WindowManager windowManager;
    @SuppressLint("StaticFieldLeak")
    private static ViewGroup windowLayout;
    ApplicationParameters parameters;
    IConnector connector;
    private boolean incomeCall = false;
    DisplayState displayState;
    HistoryArchive archive;
    private static final int NOTIFICATION_ID = 2;

    public CallReceiver(ApplicationParameters parameters, Context context) {
        this.parameters = parameters;
        connector = new CustomAuthenticator(parameters);
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        displayState = new DisplayState(context);
        archive = HistoryArchive.getArchive(context);
    }

    boolean screenLock;
    boolean currentScreenState;
    static boolean notificationShow = false;

    @Override
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)){
            screenLock = true;
        } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)){
            screenLock = false;
        }

        if (intent.getAction().equals("android.intent.action.PHONE_STATE")){
            String extra = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
            if (extra.equals(TelephonyManager.EXTRA_STATE_RINGING)){
                if (!incomeCall) {
                    Toast.makeText(context, "Income call", Toast.LENGTH_SHORT).show();
                    if (parameters.isEnable()) {
                        currentScreenState = screenLock;
                        String number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
                        incomeCall = true;
                        DoRequest(context, number);
                    }
                }
            } else if(extra.equals(TelephonyManager.EXTRA_STATE_OFFHOOK)){
                if (incomeCall) {
                    closeWindow(context);
                    incomeCall = false;
                }
            } else if(extra.equals(TelephonyManager.EXTRA_STATE_IDLE)){
                if (incomeCall) {
                    closeWindow(context);
                    incomeCall = false;
                }
            }

        }
    }

    void DoRequest(final Context context, final String number){
        Log.i("DoRequest", "Do");
        @SuppressLint("HandlerLeak") final Handler handler = new Handler(){
            @Override
            public void handleMessage(Message msg) {
                Bundle bundle = msg.getData();
                Log.i("Data", bundle.toString());
                String data = bundle.getString("data");
                String contact = data;

                try {
                    JSONObject json = new JSONObject(data);
                    contact = json.getString("Contact");
                    archive.addToArchive(HistoryType.income, number, contact);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                if (incomeCall) {
                    if(currentScreenState){
                        CallReceiver.ShowNotification(context, contact);
                    } else {
                        CallReceiver.ShowMessage(context, contact);
                    }

                }
            }
        };
        Runnable runnable = () -> {
            Message msg = handler.obtainMessage();
            Bundle bundle = new Bundle();
            try {
                bundle.putString("data", connector.Request(number));
            } catch (IOException e) {
                e.printStackTrace();
            }
            msg.setData(bundle);
            handler.sendMessage(msg);
        };
        Thread thread = new Thread(runnable);
        thread.start();
    }

    static Toast toast;
    static CountDownTimer toastCountDown;
    private static void ShowToast(Context context, String contact) {

            int toastDurationInMilliSeconds = 60000;
            toast = Toast.makeText(context, contact, Toast.LENGTH_SHORT);

            toastCountDown = new CountDownTimer(toastDurationInMilliSeconds, 1000 ) {
                public void onTick(long millisUntilFinished) {
                    toast.show();
                }
                public void onFinish() {
                    toast.cancel();
                }
            };

            TextView view = toast.getView().findViewById(android.R.id.message);
            view.setTextSize(16);
            view.setGravity(Gravity.CENTER);
            view.setTextColor(Color.LTGRAY);
            toast.show();
            toastCountDown.start();
    }

    private static void ShowNotification(Context context, String contact) {
        notificationShow = true;
        Notificator.show(context, contact, NOTIFICATION_ID);
    }

    public static void ShowMessage(Context context, String phoneNumber) {

        LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        int LAYOUT_FLAG;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_PHONE;
        }
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                LAYOUT_FLAG,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.CENTER;

        windowLayout = (ViewGroup) layoutInflater.inflate(R.layout.info, null);

        TextView textViewNumber= windowLayout.findViewById(R.id.textViewNumber);
        Button buttonClose= windowLayout.findViewById(R.id.closeButton);
//        TextView details = windowLayout.findViewById(R.id.details);

        textViewNumber.setText(phoneNumber);

        buttonClose.setOnClickListener(v -> closeWindow(context));

        windowManager.addView(windowLayout, params);

    }

    private static void closeWindow(Context context) {
        if (windowLayout !=null){
            windowManager.removeView(windowLayout);
            windowLayout =null;
        }

        if (notificationShow){
            Notificator.close(context, NOTIFICATION_ID);
        }

        if (toastCountDown != null) {
            toastCountDown.cancel();
        }
    }
}
