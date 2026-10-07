package com.example.quranlibrary;

import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.quranlibrary.ui.MainViewModel;
import com.example.quranlibrary.ui.SectionsAdapter;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class MainActivity extends AppCompatActivity {

    private MainViewModel viewModel;
    private SectionsAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        RecyclerView recyclerView = findViewById(R.id.sectionsRecyclerView);
        FloatingActionButton fab = findViewById(R.id.addSectionFab);

        adapter = new SectionsAdapter(section -> {
            Toast.makeText(this, "اخترت: " + section.name, Toast.LENGTH_SHORT).show();
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        viewModel.getAllSections().observe(this, sections -> adapter.submitList(sections));

        fab.setOnClickListener(v -> showAddSectionDialog());
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
}
