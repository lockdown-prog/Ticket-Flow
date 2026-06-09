package com.example.ticket;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;

public class SearchTicketFragment extends Fragment implements TicketAdapter.OnTicketClickedListener {

    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    private String mParam1;
    private String mParam2;

    RecyclerView recyclerView;
    ArrayList<TicketModel> tickets = new ArrayList<>();
    ArrayList<TicketModel> allTickets = new ArrayList<>(); // Store all tickets for filtering
    TicketAdapter adapter;

    LinearLayout editorLayout;
    private BottomSheetBehavior<LinearLayout> bottomSheetBehavior;
    private View bottomSheetView;

    // UI Elements
    private TextInputEditText searchBox;
    private TextInputEditText etSubject, etAmount, etFilerName;
    private CheckBox cbSettled;
    private MaterialButton btnDelete, btnSave, btnCancel;

    private TicketModel currentTicket;
    private boolean isSearchFocused = false;
    private String currentSearchText = "";

    public SearchTicketFragment() {
        // Required empty public constructor
    }

    public static SearchTicketFragment newInstance(String param1, String param2) {
        SearchTicketFragment fragment = new SearchTicketFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }

        // Handle back button to close bottom sheet
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (bottomSheetBehavior != null &&
                        bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                } else {
                    setEnabled(false);
                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                }
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(this, callback);
    }

    @SuppressLint("MissingInflatedId")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_search_ticket, container, false);

        initializeViews(view);
        setupBottomSheet();
        setupRecyclerView();
        loadDataFromDatabase();
        setupSearchView();
        setupBottomSheetButtons();

        return view;
    }

    private void initializeViews(View view) {
        recyclerView = view.findViewById(R.id.recyclerView);
        editorLayout = view.findViewById(R.id.editorLayout);
        searchBox = view.findViewById(R.id.searchBox);

        // Initialize bottom sheet UI elements
        bottomSheetView = editorLayout;
        etFilerName = bottomSheetView.findViewById(R.id.filerName);
        etSubject = bottomSheetView.findViewById(R.id.subject);
        etAmount = bottomSheetView.findViewById(R.id.amount);
        cbSettled = bottomSheetView.findViewById(R.id.settled);
        btnDelete = bottomSheetView.findViewById(R.id.btnDelete);
        btnSave = bottomSheetView.findViewById(R.id.btnSave);
        btnCancel = bottomSheetView.findViewById(R.id.btnCancel);
    }

    private void setupBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(editorLayout);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        bottomSheetBehavior.setPeekHeight(1);
        bottomSheetBehavior.setHideable(false);
    }

    private void setupRecyclerView() {
        adapter = new TicketAdapter(tickets, this);
        recyclerView.setAdapter(adapter);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getContext());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
    }

    private void loadDataFromDatabase() {
        DBHelper dbHelper = new DBHelper(getContext());
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = dbHelper.getAllData(db);

        allTickets.clear();
        tickets.clear();

        if (cursor.moveToFirst()) {
            do {
                try {
                    String filerName = cursor.getString(cursor.getColumnIndexOrThrow("filerName"));
                    String subject = cursor.getString(cursor.getColumnIndexOrThrow("subject"));
                    String amount = cursor.getString(cursor.getColumnIndexOrThrow("amount"));
                    String status = cursor.getString(cursor.getColumnIndexOrThrow("status"));

                    TicketModel ticket = new TicketModel(filerName, subject, amount, status);
                    allTickets.add(ticket);
                } catch (IllegalArgumentException e) {
                    // Handle case where column doesn't exist
                    e.printStackTrace();
                }
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();

        // Initially show all tickets
        tickets.addAll(allTickets);
        adapter.notifyDataSetChanged();
    }

    private void setupSearchView() {
        if (searchBox != null) {
            // When search field is clicked/focused, clear all tickets
            searchBox.setOnFocusChangeListener((v, hasFocus) -> {
                isSearchFocused = hasFocus;
                if (hasFocus) {
                    // Clear the current list
                    tickets.clear();
                    adapter.notifyDataSetChanged();
                    // Clear any existing search text
                    currentSearchText = "";
                } else {
                    // When focus is lost, optionally restore all tickets
                    // Comment this if you want to keep search results
                    if (TextUtils.isEmpty(searchBox.getText().toString())) {
                        tickets.clear();
                        tickets.addAll(allTickets);
                        adapter.notifyDataSetChanged();
                    }
                }
            });

            searchBox.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (isSearchFocused) {
                        currentSearchText = s.toString();
                        performSearch(currentSearchText);
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
        }
    }

    private void performSearch(String searchText) {
        tickets.clear();

        // If search text is empty, show empty list (don't show any tickets)
        if (TextUtils.isEmpty(searchText)) {
            adapter.notifyDataSetChanged();
            return;
        }

        String filterPattern = searchText.toLowerCase().trim();

        // Search through allTickets (the master list)
        for (TicketModel ticket : allTickets) {
            if (ticket.getFilerName() != null && ticket.getFilerName().toLowerCase().contains(filterPattern)
                    || ticket.getSubject() != null && ticket.getSubject().toLowerCase().contains(filterPattern)) {
                tickets.add(ticket);
            }
        }

        adapter.notifyDataSetChanged();

        // Optional: Show message if no results found
        if (tickets.isEmpty()) {
            Toast.makeText(getContext(), "No matching tickets found", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupBottomSheetButtons() {
        if (btnDelete != null) {
            btnDelete.setOnClickListener(v -> deleteRecord());
        }

        if (btnSave != null) {
            btnSave.setOnClickListener(v -> updateRecord());
        }

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> {
                if (bottomSheetBehavior != null) {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                }
                clearBottomSheetFields();
            });
        }
    }

    private void deleteRecord() {
        if (currentTicket == null) {
            Toast.makeText(getContext(), "No record selected", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Record")
                .setMessage("Are you sure you want to delete this record?")
                .setPositiveButton("Delete", (dialog, which) -> performDelete())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performDelete() {
        DBHelper dbHelper = new DBHelper(getContext());
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // Delete using combination of filerName and subject as unique identifier
        String selection = "filerName = ? AND subject = ?";
        String[] selectionArgs = {currentTicket.getFilerName(), currentTicket.getSubject()};

        int deletedRows = db.delete("tickets", selection, selectionArgs);
        db.close();

        if (deletedRows > 0) {
            Toast.makeText(getContext(), "Record deleted successfully", Toast.LENGTH_SHORT).show();
            refreshData();
            if (bottomSheetBehavior != null) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
            clearBottomSheetFields();
            currentTicket = null;
        } else {
            Toast.makeText(getContext(), "Failed to delete record", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateRecord() {
        if (currentTicket == null) {
            Toast.makeText(getContext(), "No record selected", Toast.LENGTH_SHORT).show();
            return;
        }

        String updatedSubject = etSubject.getText().toString().trim();
        String updatedAmount = etAmount.getText().toString().trim();
        String updatedStatus = cbSettled.isChecked() ? "SETTLED" : "PENDING";

        if (TextUtils.isEmpty(updatedSubject)) {
            etSubject.setError("Subject is required");
            return;
        }

        if (TextUtils.isEmpty(updatedAmount)) {
            etAmount.setError("Amount is required");
            return;
        }

        DBHelper dbHelper = new DBHelper(getContext());
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("subject", updatedSubject);
        values.put("amount", updatedAmount);
        values.put("status", updatedStatus);

        // Update using combination of filerName and original subject
        String selection = "filerName = ? AND subject = ?";
        String[] selectionArgs = {currentTicket.getFilerName(), currentTicket.getSubject()};

        int updatedRows = db.update("tickets", values, selection, selectionArgs);
        db.close();

        if (updatedRows > 0) {
            Toast.makeText(getContext(), "Record updated successfully", Toast.LENGTH_SHORT).show();
            refreshData();
            if (bottomSheetBehavior != null) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
            clearBottomSheetFields();
            currentTicket = null;
        } else {
            Toast.makeText(getContext(), "Failed to update record", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshData() {
        // Reload all tickets from database
        DBHelper dbHelper = new DBHelper(getContext());
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = dbHelper.getAllData(db);

        allTickets.clear();
        tickets.clear();

        if (cursor.moveToFirst()) {
            do {
                try {
                    String filerName = cursor.getString(cursor.getColumnIndexOrThrow("filerName"));
                    String subject = cursor.getString(cursor.getColumnIndexOrThrow("subject"));
                    String amount = cursor.getString(cursor.getColumnIndexOrThrow("amount"));
                    String status = cursor.getString(cursor.getColumnIndexOrThrow("status"));

                    TicketModel ticket = new TicketModel(filerName, subject, amount, status);
                    allTickets.add(ticket);
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                }
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();

        // Reset search state and show all tickets
        isSearchFocused = false;
        currentSearchText = "";
        tickets.addAll(allTickets);
        adapter.notifyDataSetChanged();

        // Clear search text and remove focus
        if (searchBox != null) {
            searchBox.setText("");
            searchBox.clearFocus();
        }
    }

    private void clearBottomSheetFields() {
        if (etSubject != null) etSubject.setText("");
        if (etAmount != null) etAmount.setText("");
        if (cbSettled != null) cbSettled.setChecked(false);
        if (etFilerName != null) etFilerName.setText("");
    }

    @Override
    public void onTicketClicked(TicketModel ticket) {
        currentTicket = ticket;
        if (bottomSheetBehavior != null) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }

        if (etFilerName != null) etFilerName.setText(ticket.getFilerName());
        if (etSubject != null) etSubject.setText(ticket.getSubject());
        if (etAmount != null) etAmount.setText(ticket.getPrice());
        if (cbSettled != null){
            cbSettled.setChecked(ticket.getStatus().equals("SETTLED"));
        }
    }
}