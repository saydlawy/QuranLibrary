package com.example.quranlibrary.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.quranlibrary.R;
import com.example.quranlibrary.data.db.Section;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class DownloadActivity extends AppCompatActivity {

    private DownloadViewModel viewModel;
    private EditText urlInput;
    private EditText titleInput;
    private Spinner sectionsSpinner;
    private Button downloadButton;
    private ProgressBar progressBar;

    private final List<Section> sectionsList = new ArrayList<>();
    private ArrayAdapter<String> spinnerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        urlInput = findViewById(R.id.urlInput);
        titleInput = findViewById(R.id.titleInput);
        sectionsSpinner = findViewById(R.id.sectionsSpinner);
        downloadButton = findViewById(R.id.downloadButton);
        progressBar = findViewById(R.id.downloadProgressBar);

        spinnerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, new ArrayList<>());
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sectionsSpinner.setAdapter(spinnerAdapter);

        viewModel = new ViewModelProvider(this).get(DownloadViewModel.class);

        viewModel.getAllSections().observe(this, sections -> {
            sectionsList.clear();
            if (sections != null) sectionsList.addAll(sections);
            spinnerAdapter.clear();
            for (Section s : sectionsList) spinnerAdapter.add(s.name);
            spinnerAdapter.notifyDataSetChanged();
        });

        viewModel.getIsDownloading().observe(this, isDownloading -> {
            boolean downloading = isDownloading != null && isDownloading;
            progressBar.setVisibility(downloading ? View.VISIBLE : View.GONE);
            downloadButton.setEnabled(!downloading);
        });

        viewModel.getStatusMessage().observe(this, msg -> {
            if (msg == null) return;
            // عرض الرسائل الطويلة (أخطاء) في AlertDialog، والقصيرة في Toast
            if (msg.length() > 60 || msg.startsWith("فشل")) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("تفاصيل")
                        .setMessage(msg)
                        .setPositiveButton("حسنًا", null)
                        .show();
            } else {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });

        downloadButton.setOnClickListener(v -> {
            String url = urlInput.getText().toString();
            String title = titleInput.getText().toString();
            int pos = sectionsSpinner.getSelectedItemPosition();
            if (pos < 0 || pos >= sectionsList.size()) {
                Toast.makeText(this, "الرجاء اختيار قسم", Toast.LENGTH_SHORT).show();
                return;
            }
            Section section = sectionsList.get(pos);
            // القيم الافتراضية: أفضل جودة، وضع تلقائي
            viewModel.startDownload(url, section, title, 0, "auto");
        });
    }
}
