package com.kikasports.app;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public class LeaguesAdapter extends RecyclerView.Adapter<LeaguesAdapter.LeagueViewHolder> {
    private Context context;
    private List<League> leagues;

    public LeaguesAdapter(Context context, List<League> leagues) {
        this.context = context;
        this.leagues = leagues != null ? leagues : new ArrayList<>();
    }

    @NonNull
    @Override
    public LeagueViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.league_card, parent, false);
        return new LeagueViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LeagueViewHolder holder, int position) {
        League league = leagues.get(position);

        holder.leagueName.setText(league.getName() != null ? league.getName() : "Unknown League");
        holder.countryName.setText(league.getCountry() != null ? league.getCountry() : "Unknown Country");

        // Load league logo
        if (league.getLogoUrl() != null && !league.getLogoUrl().isEmpty()) {
            Glide.with(context)
                    .load(league.getLogoUrl())
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .into(holder.leagueLogo);
        } else {
            holder.leagueLogo.setImageResource(android.R.drawable.ic_menu_gallery);
        }

        // Click listener to open league table
        holder.leagueCard.setOnClickListener(v -> {
            Intent intent = new Intent(context, LeagueTableActivity.class);
            intent.putExtra("league", league);
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return leagues.size();
    }

    public void updateLeagues(List<League> newLeagues) {
        this.leagues = newLeagues != null ? newLeagues : new ArrayList<>();
        notifyDataSetChanged();
    }

    static class LeagueViewHolder extends RecyclerView.ViewHolder {
        CardView leagueCard;
        ImageView leagueLogo;
        TextView leagueName, countryName;

        public LeagueViewHolder(@NonNull View itemView) {
            super(itemView);
            leagueCard = itemView.findViewById(R.id.leagueCard);
            leagueLogo = itemView.findViewById(R.id.leagueLogo);
            leagueName = itemView.findViewById(R.id.leagueName);
            countryName = itemView.findViewById(R.id.countryName);
        }
    }
}