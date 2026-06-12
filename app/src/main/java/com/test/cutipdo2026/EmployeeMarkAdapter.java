package com.test.cutipdo2026;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EmployeeMarkAdapter extends RecyclerView.Adapter<EmployeeMarkAdapter.ViewHolder> {

    private List<EmployeeBalance> employeeList;
    private Set<String> selectedEmployees = new HashSet<>();

    public EmployeeMarkAdapter(List<EmployeeBalance> employeeList) {
        this.employeeList = employeeList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_employee_checkbox, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        EmployeeBalance employee = employeeList.get(position);
        holder.tvEmployeeName.setText(employee.name);
        holder.tvEmployeeClass.setText(employee.empClass);
        
        holder.cbEmployeeMark.setOnCheckedChangeListener(null);
        holder.cbEmployeeMark.setChecked(selectedEmployees.contains(employee.name));
        holder.cbEmployeeMark.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                selectedEmployees.add(employee.name);
            } else {
                selectedEmployees.remove(employee.name);
            }
        });
    }

    @Override
    public int getItemCount() {
        return employeeList.size();
    }

    public Set<String> getSelectedEmployees() {
        return selectedEmployees;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        CheckBox cbEmployeeMark;
        TextView tvEmployeeName, tvEmployeeClass;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            cbEmployeeMark = itemView.findViewById(R.id.cbEmployeeMark);
            tvEmployeeName = itemView.findViewById(R.id.tvEmployeeName);
            tvEmployeeClass = itemView.findViewById(R.id.tvEmployeeClass);
        }
    }
}
