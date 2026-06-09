package com.example.ticket;

import android.annotation.SuppressLint;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link CreateTicketFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class CreateTicketFragment extends Fragment {

    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    private String mParam1;
    private String mParam2;

    private MaterialButton enter;
    private TextInputEditText filerName, subject, amount;
    private TextInputLayout filerNameLayout, subjectLayout, amountLayout;

    public CreateTicketFragment() {
        // Required empty public constructor
    }

    public static CreateTicketFragment newInstance(String param1, String param2) {
        CreateTicketFragment fragment = new CreateTicketFragment();
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
    }

    @SuppressLint("MissingInflatedId")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_create_ticket, container, false);

        initializeViews(view);
        setupRealTimeValidation();

        enter.setOnClickListener(v -> insertTicket());

        return view;
    }

    private void initializeViews(View view) {
        enter = view.findViewById(R.id.enter);
        filerName = view.findViewById(R.id.filerName);
        subject = view.findViewById(R.id.subject);
        amount = view.findViewById(R.id.amount);

        // Get TextInputLayout parents for error handling
        View parent = (View) filerName.getParent();
        if (parent instanceof TextInputLayout) {
            filerNameLayout = (TextInputLayout) parent;
        }

        parent = (View) subject.getParent();
        if (parent instanceof TextInputLayout) {
            subjectLayout = (TextInputLayout) parent;
        }

        parent = (View) amount.getParent();
        if (parent instanceof TextInputLayout) {
            amountLayout = (TextInputLayout) parent;
        }
    }

    private void setupRealTimeValidation() {
        // Real-time validation for Filer Name
        filerName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String name = s.toString().trim();
                if (!name.isEmpty()) {
                    if (name.length() < 2) {
                        filerName.setError("Name must be at least 2 characters");
                    } else if (name.length() > 50) {
                        filerName.setError("Name must be less than 50 characters");
                    } else {
                        filerName.setError(null);
                    }
                }
            }
        });

        // Real-time validation for Subject
        subject.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String subj = s.toString().trim();
                if (!subj.isEmpty()) {
                    if (subj.length() < 3) {
                        subject.setError("Subject must be at least 3 characters");
                    } else {
                        subject.setError(null);
                    }
                }
            }
        });

        // Real-time validation for Amount
        amount.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String amountStr = s.toString().trim();
                if (!amountStr.isEmpty()) {
                    try {
                        double value = Double.parseDouble(amountStr);
                        if (value <= 0) {
                            amount.setError("Amount must be greater than 0");
                        } else if (value > 999999999.99) {
                            amount.setError("Amount is too large");
                        } else {
                            amount.setError(null);
                        }
                    } catch (NumberFormatException e) {
                        amount.setError("Invalid amount format");
                    }
                }
            }
        });
    }

    private boolean validateRequiredFields() {
        boolean isValid = true;

        // Validate Filer Name
        String name = filerName.getText().toString().trim();
        if (TextUtils.isEmpty(name)) {
            filerName.setError("Filer name is required");
            isValid = false;
        } else if (name.length() < 2) {
            filerName.setError("Name must be at least 2 characters");
            isValid = false;
        } else if (name.length() > 50) {
            filerName.setError("Name must be less than 50 characters");
            isValid = false;
        }

        // Validate Subject
        String subj = subject.getText().toString().trim();
        if (TextUtils.isEmpty(subj)) {
            subject.setError("Subject is required");
            isValid = false;
        } else if (subj.length() < 3) {
            subject.setError("Subject must be at least 3 characters");
            isValid = false;
        } else if (subj.length() > 200) {
            subject.setError("Subject must be less than 200 characters");
            isValid = false;
        }

        // Validate Amount
        String amountStr = amount.getText().toString().trim();
        if (TextUtils.isEmpty(amountStr)) {
            amount.setError("Amount is required");
            isValid = false;
        } else {
            try {
                double value = Double.parseDouble(amountStr);
                if (value <= 0) {
                    amount.setError("Amount must be greater than 0");
                    isValid = false;
                } else if (value > 999999999.99) {
                    amount.setError("Amount is too large");
                    isValid = false;
                }
            } catch (NumberFormatException e) {
                amount.setError("Please enter a valid number");
                isValid = false;
            }
        }

        // Focus on the first invalid field
        if (!isValid) {
            if (TextUtils.isEmpty(filerName.getText().toString().trim())) {
                filerName.requestFocus();
            } else if (TextUtils.isEmpty(subject.getText().toString().trim())) {
                subject.requestFocus();
            } else if (TextUtils.isEmpty(amount.getText().toString().trim())) {
                amount.requestFocus();
            }
        }

        return isValid;
    }

    private boolean checkDuplicateRecord(String filerNameStr, String subjectStr) {
        DBHelper dbHelper = new DBHelper(getContext());
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String query = "SELECT * FROM tickets WHERE filerName = ? AND subject = ?";
        Cursor cursor = db.rawQuery(query, new String[]{filerNameStr, subjectStr});

        boolean exists = cursor.getCount() > 0;
        cursor.close();
        db.close();

        return exists;
    }

    private void insertTicket() {
        // Validate all required fields first
        if (!validateRequiredFields()) {
            Toast.makeText(getContext(), "Please fix the errors above", Toast.LENGTH_SHORT).show();
            return;
        }

        String name = filerName.getText().toString().trim();
        String subj = subject.getText().toString().trim();
        String amt = amount.getText().toString().trim();

        // Check for duplicate records
        if (checkDuplicateRecord(name, subj)) {
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Duplicate Record")
                    .setMessage("A ticket with the same filer name and subject already exists.\n\nDo you want to create it anyway?")
                    .setPositiveButton("Yes, Create", (dialog, which) -> performInsertion(name, subj, amt))
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        performInsertion(name, subj, amt);
    }

    private void performInsertion(String name, String subj, String amt) {
        DBHelper dbHelper = new DBHelper(getContext());
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        long row = dbHelper.insertData(db, name, subj, amt, "UNSETTLED");
        db.close();

        if (row < 0) {
            Toast.makeText(getContext(), "Failed to insert data", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(getContext(), "Ticket created successfully!", Toast.LENGTH_LONG).show();
            clearForm();
        }
    }

    private void clearForm() {
        filerName.setText("");
        subject.setText("");
        amount.setText("");

        // Clear all errors
        filerName.setError(null);
        subject.setError(null);
        amount.setError(null);

        // Request focus to first field
        filerName.requestFocus();
    }

    // Optional: Add a method to validate before allowing form submission
    private boolean isFormReadyForSubmission() {
        boolean isReady = true;

        String name = filerName.getText().toString().trim();
        String subj = subject.getText().toString().trim();
        String amt = amount.getText().toString().trim();

        if (TextUtils.isEmpty(name) || name.length() < 2) isReady = false;
        if (TextUtils.isEmpty(subj) || subj.length() < 3) isReady = false;
        if (TextUtils.isEmpty(amt)) isReady = false;

        try {
            if (!TextUtils.isEmpty(amt) && Double.parseDouble(amt) <= 0) isReady = false;
        } catch (NumberFormatException e) {
            isReady = false;
        }

        return isReady;
    }

    // Update the enter button color based on form validity (optional)
    private void updateButtonState() {
        if (isFormReadyForSubmission()) {
            enter.setEnabled(true);
            enter.setAlpha(1.0f);
        } else {
            enter.setEnabled(false);
            enter.setAlpha(0.5f);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Optional: Add text change listeners to update button state
        TextWatcher buttonStateWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateButtonState();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };

        filerName.addTextChangedListener(buttonStateWatcher);
        subject.addTextChangedListener(buttonStateWatcher);
        amount.addTextChangedListener(buttonStateWatcher);

        // Initially disable button if form is empty
        updateButtonState();
    }
}