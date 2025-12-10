// WIP: This activity is a temporary test implementation and not final.

package com.jhaiian.attendify.admin;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import com.shashank.sony.fancytoastlib.FancyToast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ListView listview1;
    private FloatingActionButton fab_add;
    private ArrayList<HashMap<String, Object>> listData = new ArrayList<>();
    private ListAdapter adapter;
    private AlertDialog customDialog;
    private ImageView dialogAvatarPreview;
    private Uri selectedImageUri = null;
    private static final int REQ_CODE_PICK_IMAGE = 100;
    private static final int REQ_CODE_PERMISSION = 101;
    private NetworkMonitor networkMonitor;
    private long backPressedTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        initializeUI();
        setupLogic();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (networkMonitor != null) {
            networkMonitor.startMonitoring();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (networkMonitor != null) {
            networkMonitor.stopMonitoring();
        }
    }

    @Override
    public void onBackPressed() {
        if (backPressedTime + 2000 > System.currentTimeMillis()) {
            super.onBackPressed();
            finishAffinity();
        } else {
            FancyToast.makeText(this, "Press back again to exit",
                    FancyToast.LENGTH_SHORT, FancyToast.INFO, false).show();
        }
        backPressedTime = System.currentTimeMillis();
    }

    private void initializeUI() {
        listview1 = findViewById(R.id.listview1);
        fab_add = findViewById(R.id.fab_add);
        adapter = new ListAdapter(listData);
        listview1.setAdapter(adapter);
        networkMonitor = new NetworkMonitor(this);
    }

    private void setupLogic() {
        fab_add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddPersonDialog();
            }
        });
    }

    private void showAddPersonDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(R.layout.addfabcustomdialog, null);
        builder.setView(view);
        builder.setCancelable(false);

        customDialog = builder.create();
        customDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        final TextInputLayout layoutName = view.findViewById(R.id.Dialog_Layout_Name);
        final TextInputLayout layoutGrade = view.findViewById(R.id.Dialog_Layout_Grade);
        final TextInputLayout layoutSection = view.findViewById(R.id.Dialog_Layout_Section);
        final TextInputLayout layoutPosition = view.findViewById(R.id.Dialog_Layout_Position);

        final TextInputEditText inputName = view.findViewById(R.id.Dialog_Input_Name);
        final TextInputEditText inputGrade = view.findViewById(R.id.Dialog_Input_Grade);
        final TextInputEditText inputSection = view.findViewById(R.id.Dialog_Input_Section);
        final TextInputEditText inputPosition = view.findViewById(R.id.Dialog_Input_Position);

        final LinearLayout containerStudent = view.findViewById(R.id.Dialog_Container_Student_Fields);
        final LinearLayout containerEmployee = view.findViewById(R.id.Dialog_Container_Employee_Fields);
        final LinearLayout containerWorkDays = view.findViewById(R.id.Dialog_Container_WorkDays);

        final ChipGroup chipGroupRoles = view.findViewById(R.id.Dialog_ChipGroup_Roles);
        final ChipGroup chipGroupDays = view.findViewById(R.id.Dialog_ChipGroup_Days);

        final TextView textDaysTitle = view.findViewById(R.id.Dialog_Title_Days);

        dialogAvatarPreview = view.findViewById(R.id.Dialog_Image_Preview);
        View btnCancel = view.findViewById(R.id.Dialog_Btn_Cancel);
        View btnSave = view.findViewById(R.id.Dialog_Btn_Save);
        View imageClickArea = view.findViewById(R.id.Dialog_Container_ImageUpload);

        selectedImageUri = null;

        containerWorkDays.setVisibility(View.VISIBLE);
        textDaysTitle.setText("School Days");

        TextWatcher clearErrorWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (inputName.hasFocus()) layoutName.setError(null);
                if (inputGrade.hasFocus()) layoutGrade.setError(null);
                if (inputSection.hasFocus()) layoutSection.setError(null);
                if (inputPosition.hasFocus()) layoutPosition.setError(null);
            }
            @Override public void afterTextChanged(Editable s) {}
        };

        inputName.addTextChangedListener(clearErrorWatcher);
        inputGrade.addTextChangedListener(clearErrorWatcher);
        inputSection.addTextChangedListener(clearErrorWatcher);
        inputPosition.addTextChangedListener(clearErrorWatcher);

        chipGroupRoles.setOnCheckedChangeListener(new ChipGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(ChipGroup group, int checkedId) {
                containerStudent.setVisibility(View.GONE);
                containerEmployee.setVisibility(View.GONE);
                containerWorkDays.setVisibility(View.VISIBLE);

                layoutGrade.setError(null);
                layoutSection.setError(null);
                layoutPosition.setError(null);

                if (checkedId == R.id.Dialog_Chip_Student) {
                    containerStudent.setVisibility(View.VISIBLE);
                    textDaysTitle.setText("School Days");
                } else if (checkedId == R.id.Dialog_Chip_Employee || checkedId == R.id.Dialog_Chip_Teacher) {
                    if (checkedId == R.id.Dialog_Chip_Employee) {
                        containerEmployee.setVisibility(View.VISIBLE);
                    }
                    textDaysTitle.setText("Work Shift Days");
                }
            }
        });

        imageClickArea.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkPermission()) {
                    openGallery();
                } else {
                    requestPermission();
                }
            }
        });

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                customDialog.dismiss();
            }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = inputName.getText().toString().trim();
                if (name.isEmpty()) {
                    layoutName.setError("Name is required");
                    inputName.requestFocus();
                    return;
                }

                int selectedChipId = chipGroupRoles.getCheckedChipId();
                HashMap<String, Object> map = new HashMap<>();
                map.put("name", name);

                if (selectedImageUri != null) {
                    map.put("image_uri", selectedImageUri.toString());
                }

                List<Integer> checkedDays = chipGroupDays.getCheckedChipIds();
                if (checkedDays.isEmpty()) {
                    FancyToast.makeText(MainActivity.this, "Please select at least one day",
                            FancyToast.LENGTH_SHORT, FancyToast.ERROR, false).show();
                    return;
                }
                map.put("work_days_count", checkedDays.size());

                if (selectedChipId == R.id.Dialog_Chip_Student) {
                    String grade = inputGrade.getText().toString().trim();
                    String section = inputSection.getText().toString().trim();

                    if (grade.isEmpty()) {
                        layoutGrade.setError("Grade is required");
                        inputGrade.requestFocus();
                        return;
                    }
                    if (section.isEmpty()) {
                        layoutSection.setError("Section is required");
                        inputSection.requestFocus();
                        return;
                    }

                    map.put("role", "Student");
                    map.put("description", "Grade " + grade + " - " + section);
                    map.put("status", "present");

                } else if (selectedChipId == R.id.Dialog_Chip_Teacher) {
                    map.put("role", "Teacher");
                    map.put("description", "Faculty Member");
                    map.put("status", "active");

                } else if (selectedChipId == R.id.Dialog_Chip_Employee) {
                    String position = inputPosition.getText().toString().trim();

                    if (position.isEmpty()) {
                        layoutPosition.setError("Position is required");
                        inputPosition.requestFocus();
                        return;
                    }

                    map.put("role", "Employee");
                    map.put("description", position);
                    map.put("status", "active");
                }

                listData.add(map);
                adapter.notifyDataSetChanged();
                customDialog.dismiss();

                FancyToast.makeText(MainActivity.this, "Person Added Successfully",
                        FancyToast.LENGTH_SHORT, FancyToast.SUCCESS, false).show();
            }
        });

        customDialog.show();
        customDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        customDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        inputName.requestFocus();
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, REQ_CODE_PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK && requestCode == REQ_CODE_PICK_IMAGE) {
            if (data != null) {
                selectedImageUri = data.getData();
                if (dialogAvatarPreview != null) {
                    dialogAvatarPreview.setImageURI(selectedImageUri);
                }
            }
        }
    }

    private boolean checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_MEDIA_IMAGES}, REQ_CODE_PERMISSION);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_CODE_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CODE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openGallery();
            } else {
                FancyToast.makeText(this, "Permission denied",
                        FancyToast.LENGTH_SHORT, FancyToast.ERROR, false).show();
            }
        }
    }

    public class ListAdapter extends BaseAdapter {
        ArrayList<HashMap<String, Object>> _data;
        public ListAdapter(ArrayList<HashMap<String, Object>> _arr) { _data = _arr; }
        @Override public int getCount() { return _data.size(); }
        @Override public HashMap<String, Object> getItem(int _index) { return _data.get(_index); }
        @Override public long getItemId(int _index) { return _index; }
        @Override public View getView(final int _position, View _v, ViewGroup _container) {
            LayoutInflater _inflater = getLayoutInflater();
            View _view = _v != null ? _v : _inflater.inflate(R.layout.profilelist, null);
            final TextView textName = _view.findViewById(R.id.Name);
            final TextView textDesc = _view.findViewById(R.id.Description);
            final ImageView imageAvatar = _view.findViewById(R.id.Avatar);
            final View statusIndicator = _view.findViewById(R.id.Status_Indicator);

            HashMap<String, Object> item = _data.get(_position);
            textName.setText(item.get("name") != null ? item.get("name").toString() : "");
            textDesc.setText(item.get("description") != null ? item.get("description").toString() : "");

            if (item.containsKey("image_uri")) {
                imageAvatar.setImageURI(Uri.parse(item.get("image_uri").toString()));
            } else {
                imageAvatar.setImageResource(R.drawable.default_image);
            }

            String role = item.get("role") != null ? item.get("role").toString() : "Student";
            if (role.equals("Student")) {
                statusIndicator.setBackgroundColor(Color.parseColor("#4CAF50"));
            } else if (role.equals("Teacher")) {
                statusIndicator.setBackgroundColor(Color.parseColor("#2196F3"));
            } else {
                statusIndicator.setBackgroundColor(Color.parseColor("#FFC107"));
            }
            return _view;
        }
    }
}