package com.example.quranlibrary;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
        }
        Python py = Python.getInstance();
        PyObject result = py.getModule("test").callAttr("greet");

        TextView tv = new TextView(this);
        tv.setText("Java + Python + Kotlin\n\n" + result.toString());
        tv.setTextSize(20);
        setContentView(tv);
    }
}
