package com.fgil55.amazfit.musicremote.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v7.widget.AppCompatTextView;
import android.util.AttributeSet;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import static android.content.Intent.ACTION_TIME_TICK;

public class CurrentTimeTextView extends AppCompatTextView {

    public static final String TIME_PATTERN_12H = "hh:mm a";
    public static final String TIME_PATTERN_24H = "HH:mm";

    private final SimpleDateFormat simpleDateFormat = new SimpleDateFormat(TIME_PATTERN_24H, Locale.US);

    public CurrentTimeTextView(Context context) {
        super(context);
        init(context);
    }

    public CurrentTimeTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public CurrentTimeTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        refresh_time();

        context.registerReceiver(
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        refresh_time();
                    }
                },
                new IntentFilter(ACTION_TIME_TICK)
        );
    }

    private void refresh_time() {
        this.setText(simpleDateFormat.format(Calendar.getInstance().getTime()));
    }

}
