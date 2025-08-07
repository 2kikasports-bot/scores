package com.kikasports.app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public class LeagueTableAdapter extends RecyclerView.Adapter<LeagueTableAdapter.StandingViewHolder> {
    private Context context;
    private List<TeamStanding> standings;

    public LeagueTableAdapter(Context context, List<TeamStanding> standings) {
        this.context = context;
        this.standings = standings != null ? standings : new ArrayList<>();
    }

    @NonNull
    @Override
    public StandingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.team_standing_item, parent, false);
        return new StandingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StandingViewHolder holder, int position) {
        TeamStanding standing = standings.get(position);

        holder.positionText.setText(String.valueOf(standing.getPosition()));
        holder.teamNameText.setText(standing.getTeamName() != null ? standing.getTeamName() : "Unknown Team");
        holder.playedText.setText(String.valueOf(standing.getPlayed()));
        holder.winsText.setText(String.valueOf(standing.getWins()));
        holder.drawsText.setText(String.valueOf(standing.getDraws()));
        holder.lossesText.setText(String.valueOf(standing.getLosses()));
        holder.goalDifferenceText.setText(standing.getGoalDifference() >= 0 ? 
            "+" + standing.getGoalDifference() : String.valueOf(standing.getGoalDifference()));
        holder.pointsText.setText(String.valueOf(standing.getPoints()));

        // Load team logo
        if (standing.getTeamLogo() != null && !standing.getTeamLogo().isEmpty()) {
            Glide.with(context)
                    .load(standing.getTeamLogo())
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .into(holder.teamLogo);
        } else {
            holder.teamLogo.setImageResource(android.R.drawable.ic_menu_gallery);
        }

        // Set background color based on position (Champions League, Europa League, Relegation)
        if (standing.getPosition() <= 4) {
            holder.itemView.setBackgroundColor(context.getColor(android.R.color.holo_green_light));
        } else if (standing.getPosition() <= 6) {
            holder.itemView.setBackgroundColor(context.getColor(android.R.color.holo_orange_light));
        } else if (standing.getPosition() >= 18) {
            holder.itemView.setBackgroundColor(context.getColor(android.R.color.holo_red_light));
        } else {
            holder.itemView.setBackgroundColor(context.getColor(android.R.color.transparent));
        }
    }

    @Override
    public int getItemCount() {
        return standings.size();
    }

    public void updateStandings(List<TeamStanding> newStandings) {
        this.standings = newStandings != null ? newStandings : new ArrayList<>();
        notifyDataSetChanged();
    }

    static class StandingViewHolder extends RecyclerView.ViewHolder {
        TextView positionText, teamNameText, playedText, winsText, drawsText, lossesText, goalDifferenceText, pointsText;
        ImageView teamLogo;

        public StandingViewHolder(@NonNull View itemView) {
            super(itemView);
            positionText = itemView.findViewById(R.id.positionText);
            teamLogo = itemView.findViewById(R.id.teamLogo);
            teamNameText = itemView.findViewById(R.id.teamNameText);
            playedText = itemView.findViewById(R.id.playedText);
            winsText = itemView.findViewById(R.id.winsText);
            drawsText = itemView.findViewById(R.id.drawsText);
            lossesText = itemView.findViewById(R.id.lossesText);
            goalDifferenceText = itemView.findViewById(R.id.goalDifferenceText);
            pointsText = itemView.findViewById(R.id.pointsText);
        }
    }
}