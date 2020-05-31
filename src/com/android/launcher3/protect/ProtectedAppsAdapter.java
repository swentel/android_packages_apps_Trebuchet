package com.android.launcher3.protect;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.launcher3.R;

import java.util.ArrayList;
import java.util.List;

public class ProtectedAppsAdapter extends
        RecyclerView.Adapter<ProtectedAppsAdapter.ProtectedAppViewHolder> {
    private List<ProtectedComponent> mComponents = new ArrayList<>();
    private final IProtectedApp mOnItemChangeListener;
    private Context context;

    ProtectedAppsAdapter(IProtectedApp listener, Context context) {
        mOnItemChangeListener = listener;
        this.context = context;
    }

    void updateAppList(List<ProtectedComponent> components) {
        mComponents = components;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ProtectedAppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ProtectedAppViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_protected_app, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ProtectedAppViewHolder holder, int position) {
        holder.bind(mComponents.get(position));
    }

    @Override
    public int getItemCount() {
        return mComponents.size();
    }

    class ProtectedAppViewHolder extends RecyclerView.ViewHolder {
        private View mView;
        private ImageView mAppIcon;
        private TextView mAppLabel;
        private ImageButton mAppLaunch;
        private ImageView mProtectedSwitch;

        ProtectedAppViewHolder(View itemView) {
            super(itemView);
            mView = itemView;
            mAppIcon = itemView.findViewById(R.id.item_protected_app_icon);
            mAppLabel = itemView.findViewById(R.id.item_protected_app_title);
            mAppLaunch = itemView.findViewById(R.id.item_protected_app_launch);
            mProtectedSwitch = itemView.findViewById(R.id.item_protected_app_switch);
        }

        void bind(ProtectedComponent component) {
            mAppIcon.setImageDrawable(component.icon);
            mAppLabel.setText(component.label);

            mProtectedSwitch.setImageResource(component.isProtected ?
                R.drawable.ic_protected_locked : R.drawable.ic_protected_unlocked);

            mAppLabel.setOnClickListener(v -> {
                component.isProtected = !component.isProtected;

                mProtectedSwitch.setImageResource(component.isProtected ?
                        R.drawable.avd_protected_lock : R.drawable.avd_protected_unlock);
                AnimatedVectorDrawable avd = (AnimatedVectorDrawable)
                        mProtectedSwitch.getDrawable();
                avd.start();

                mOnItemChangeListener.onItemChanged(component.packageName, component.isProtected);
            });

            mAppLaunch.setOnClickListener(v -> {
                try {
                    Intent i = context.getPackageManager().getLaunchIntentForPackage(component.packageName);
                    context.startActivity(i);
                }
                catch (Exception ignored) {}
            });
        }
    }

    public interface IProtectedApp {
        void onItemChanged(String packageName, boolean isNowHidden);
    }
}
