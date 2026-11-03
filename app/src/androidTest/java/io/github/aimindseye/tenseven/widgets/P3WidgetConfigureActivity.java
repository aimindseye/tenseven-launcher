package io.github.aimindseye.tenseven.widgets;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;

/** Configuration fixture launched by Android in the standalone test APK process. */
public class P3WidgetConfigureActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);
        int id = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        Button save = new Button(this);
        save.setText("P3 save widget");
        save.setOnClickListener(view -> {
            new P3WidgetProvider().onUpdate(this, AppWidgetManager.getInstance(this), new int[] {id});
            setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id));
            finish();
        });
        layout.addView(save);
        Button cancel = new Button(this);
        cancel.setText("P3 cancel widget");
        cancel.setOnClickListener(view -> finish());
        layout.addView(cancel);
        setContentView(layout);
    }
}
