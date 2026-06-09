package com.example.ticket;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class TicketAdapter extends RecyclerView.Adapter<TicketAdapter.MyViewHolder> {

    private ArrayList<TicketModel> ticketList;

    public interface OnTicketClickedListener {
        void onTicketClicked(TicketModel ticket);
    }

    private OnTicketClickedListener listener;

    public TicketAdapter(ArrayList<TicketModel> tickets, OnTicketClickedListener listener) {
        this.ticketList = tickets;
        this.listener = listener;
    }

    // Update the data in the adapter
    public void updateData(ArrayList<TicketModel> newTickets) {
        this.ticketList.clear();
        this.ticketList.addAll(newTickets);
        notifyDataSetChanged();
    }

    public class MyViewHolder extends RecyclerView.ViewHolder {
        TextView filerName;
        TextView subject;
        TextView value;
        TextView status;

        public MyViewHolder(@NonNull View itemView) {
            super(itemView);
            filerName = itemView.findViewById(R.id.filerName);
            subject = itemView.findViewById(R.id.subject);
            value = itemView.findViewById(R.id.valuePrice);
            status = itemView.findViewById(R.id.status);
        }
    }

    @NonNull
    @Override
    public TicketAdapter.MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.filers_list_layout, parent, false);
        return new MyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TicketAdapter.MyViewHolder holder, int position) {
        TicketModel ticket = ticketList.get(position);

        holder.filerName.setText(ticket.getFilerName());
        holder.subject.setText(ticket.getSubject());
        holder.value.setText("$" + ticket.getPrice());
        holder.status.setText(ticket.getStatus());

        // Set status background and text color based on status
        if (ticket.getStatus().equals("SETTLED")) {
            // Settled status - Dark green text with transparent dark green background
            holder.status.setTextColor(Color.parseColor("#20A005")); // Dark green
            holder.status.setBackgroundColor(Color.parseColor("#331B5E20")); // Transparent dark green (20% opacity)
        } else {
            // Unsettled/Pending status - Orange text with transparent orange background
            holder.status.setTextColor(Color.parseColor("#FF9800")); // Orange
            holder.status.setBackgroundColor(Color.parseColor("#33FF9800")); // Transparent orange (20% opacity)
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTicketClicked(ticket);
            }
        });
    }

    @Override
    public int getItemCount() {
        return ticketList.size();
    }
}