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

public class MatchAdapter extends RecyclerView.Adapter<MatchAdapter.MatchViewHolder> {
    private Context context;
    private List<Match> matches;
    private OnFavoriteClickListener favoriteClickListener;

    public interface OnFavoriteClickListener {
        void onFavoriteClick(Match match, int position);
    }

    public MatchAdapter(Context context, List<Match> matches) {
        this.context = context;
        this.matches = matches;
    }

    public void setOnFavoriteClickListener(OnFavoriteClickListener listener) {
        this.favoriteClickListener = listener;
    }

    @NonNull
    @Override
    public MatchViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.match_card, parent, false);
        return new MatchViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MatchViewHolder holder, int position) {
        Match match = matches.get(position);

        // Set competition name
        if (holder.competitionName != null) {
            holder.competitionName.setText(match.getCompetition() != null ? match.getCompetition() : "Unknown Competition");
        }

        // Set team names
        if (holder.homeTeamName != null) {
            holder.homeTeamName.setText(match.getHomeTeam() != null ? match.getHomeTeam() : "Home Team");
        }
        if (holder.awayTeamName != null) {
            holder.awayTeamName.setText(match.getAwayTeam() != null ? match.getAwayTeam() : "Away Team");
        }

        // Set favorite star
        if (holder.favoriteIcon != null) {
            holder.favoriteIcon.setImageResource(match.isFavorite() ?
                    android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off);
        }

        // Handle live matches vs scheduled matches
        if (match.isLive()) {
            // Show live status and score for live matches
            if (holder.matchStatus != null) {
                holder.matchStatus.setVisibility(View.VISIBLE);
                holder.matchStatus.setText("LIVE");
            }
            if (holder.matchScore != null) {
                holder.matchScore.setVisibility(View.VISIBLE);
                // Display actual scores for live matches
                String homeScore = (match.getHomeScore() != null && !match.getHomeScore().isEmpty()) ? match.getHomeScore() : "0";
                String awayScore = (match.getAwayScore() != null && !match.getAwayScore().isEmpty()) ? match.getAwayScore() : "0";
                holder.matchScore.setText(homeScore + " - " + awayScore);
            }
        } else {
            // Hide live status for scheduled matches
            if (holder.matchStatus != null) {
                holder.matchStatus.setVisibility(View.GONE);
            }

            // Show kickoff time for scheduled matches (no scores)
            if (holder.matchScore != null) {
                holder.matchScore.setVisibility(View.VISIBLE);
                String kickoffTime = match.getTime() != null ? match.getTime() : "TBD";
                holder.matchScore.setText(kickoffTime);
            }
        }

        // Load home team logo
        if (holder.homeTeamLogo != null) {
            if (match.getHomeTeamLogo() != null && !match.getHomeTeamLogo().isEmpty()) {
                Glide.with(context)
                        .load(match.getHomeTeamLogo())
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_gallery)
                        .into(holder.homeTeamLogo);
            } else {
                holder.homeTeamLogo.setImageResource(android.R.drawable.ic_menu_gallery);
            }
        }

        // Load away team logo
        if (holder.awayTeamLogo != null) {
            if (match.getAwayTeamLogo() != null && !match.getAwayTeamLogo().isEmpty()) {
                Glide.with(context)
                        .load(match.getAwayTeamLogo())
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_gallery)
                        .into(holder.awayTeamLogo);
            } else {
                holder.awayTeamLogo.setImageResource(android.R.drawable.ic_menu_gallery);
            }
        }

        // Match card click listener - Navigate to MatchDetailsActivity
        if (holder.matchCard != null) {
            holder.matchCard.setOnClickListener(v -> {
                Intent intent = new Intent(context, MatchDetailsActivity.class);
                intent.putExtra("match", match);
                context.startActivity(intent);
            });
        }

        // Favorite click listener
        if (holder.favoriteIcon != null) {
            holder.favoriteIcon.setOnClickListener(v -> {
                if (favoriteClickListener != null) {
                    favoriteClickListener.onFavoriteClick(match, position);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return matches != null ? matches.size() : 0;
    }

    public void updateMatches(List<Match> newMatches) {
        this.matches = newMatches != null ? newMatches : new ArrayList<>();
        notifyDataSetChanged();
    }

    static class MatchViewHolder extends RecyclerView.ViewHolder {
        CardView matchCard;
        TextView competitionName, homeTeamName, awayTeamName, matchStatus, matchScore;
        ImageView homeTeamLogo, awayTeamLogo, favoriteIcon;

        public MatchViewHolder(@NonNull View itemView) {
            super(itemView);
            matchCard = itemView.findViewById(R.id.matchCard);
            competitionName = itemView.findViewById(R.id.competition_name);
            homeTeamName = itemView.findViewById(R.id.home_team_name);
            awayTeamName = itemView.findViewById(R.id.away_team_name);
            matchStatus = itemView.findViewById(R.id.match_status);
            matchScore = itemView.findViewById(R.id.match_score);
            homeTeamLogo = itemView.findViewById(R.id.home_team_logo);
            awayTeamLogo = itemView.findViewById(R.id.away_team_logo);
            favoriteIcon = itemView.findViewById(R.id.favorite_icon);
        }
    }
}