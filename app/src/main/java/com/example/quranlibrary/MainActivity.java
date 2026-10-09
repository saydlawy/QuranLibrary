package com.example.quranlibrary;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.net.Uri;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.quranlibrary.ui.DownloadActivity;
import com.example.quranlibrary.ui.MainViewModel;
import com.example.quranlibrary.ui.SectionsAdapter;
import com.example.quranlibrary.ui.SectionVideosActivity;
import com.example.quranlibrary.data.transfer.LibraryTransferManager;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private MainViewModel viewModel;
    private SectionsAdapter adapter;

    private final ExecutorService transferExecutor = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<String> exportLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/zip"),
                    uri -> {
                        if (uri != null) runTransfer(uri, true);
                    });

    private final ActivityResultLauncher<String[]> importLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                    uri -> {
                        if (uri != null) runTransfer(uri, false);
                    });

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) {
                    Toast.makeText(this,
                            "الإشعارات مطلوبة لعرض تقدم التحميل",
                            Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        RecyclerView recyclerView = findViewById(R.id.sectionsRecyclerView);
        FloatingActionButton fab = findViewById(R.id.addSectionFab);

        adapter = new SectionsAdapter(section -> {
            Intent intent = new Intent(this, SectionVideosActivity.class);
            intent.putExtra(SectionVideosActivity.EXTRA_SECTION_ID, section.id);
            intent.putExtra(SectionVideosActivity.EXTRA_SECTION_NAME, section.name);
            startActivity(intent);
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        viewModel.getAllSections().observe(this, sections -> adapter.submitList(sections));

        fab.setOnClickListener(v -> showAddSectionDialog());

        requestNotificationPermissionIfNeeded();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_download) {
            startActivity(new Intent(this, DownloadActivity.class));
            return true;
        }
        if (item.getItemId() == R.id.action_export_library) {
            exportLauncher.launch("quran-library-backup.zip");
            return true;
        }
        if (item.getItemId() == R.id.action_import_library) {
            importLauncher.launch(new String[]{"application/zip", "application/octet-stream"});
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void runTransfer(Uri uri, boolean export) {
        Toast.makeText(this, export ? "جاري تجهيز النسخة الاحتياطية..." : "جاري استيراد المكتبة...",
                Toast.LENGTH_LONG).show();
        transferExecutor.execute(() -> {
            try {
                String result = export
                        ? LibraryTransferManager.exportToUri(getApplicationContext(), uri)
                        : LibraryTransferManager.importFromUri(getApplicationContext(), uri);
                runOnUiThread(() -> new MaterialAlertDialogBuilder(this)
                        .setTitle(export ? "اكتمل التصدير" : "اكتمل الاستيراد")
                        .setMessage(result)
                        .setPositiveButton("حسنًا", null)
                        .show());
            } catch (Exception e) {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                runOnUiThread(() -> new MaterialAlertDialogBuilder(this)
                        .setTitle("تعذر إتمام العملية")
                        .setMessage(message)
                        .setPositiveButton("حسنًا", null)
                        .show());
            }
        });
    }

    private void showAddSectionDialog() {
        EditText input = new EditText(this);
        input.setHint(R.string.new_section_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT);

        FrameLayout container = new FrameLayout(this);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding, padding, padding);
        container.addView(input);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.add_section)
                .setView(container)
                .setPositiveButton(R.string.add, (dialog, which) -> {
                    String name = input.getText().toString();
                    if (!name.trim().isEmpty()) {
                        viewModel.addSection(name);
                    } else {
                        Toast.makeText(this, R.string.empty_sections, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }
}
