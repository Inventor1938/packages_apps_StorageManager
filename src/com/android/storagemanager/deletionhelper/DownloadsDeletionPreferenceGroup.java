/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.storagemanager.deletionhelper;

import android.content.Context;
import android.support.v7.preference.Preference;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.util.AttributeSet;
import android.view.View;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.MetricsProto.MetricsEvent;
import com.android.storagemanager.PreferenceListCache;
import com.android.storagemanager.R;

import java.io.File;
import java.util.Set;

/**
 * DownloadsDeletionPreferenceGroup defines a checkable preference group which contains
 * downloads file deletion preferences.
 */
public class DownloadsDeletionPreferenceGroup extends CollapsibleCheckboxPreferenceGroup
        implements DeletionType.FreeableChangedListener, Preference.OnPreferenceChangeListener {
    private DownloadsDeletionType mDeletionType;
    private DeletionType.FreeableChangedListener mListener;

    public DownloadsDeletionPreferenceGroup(Context context) {
        this(context, null);
    }

    public DownloadsDeletionPreferenceGroup(Context context, AttributeSet attrs) {
        super(context, attrs);
        setChecked(true);
        setOrderingAsAdded(false);
        setOnPreferenceChangeListener(this);
    }

    /**
     * Set up a deletion type to get info for the preference group.
     * @param type A {@link DownloadsDeletionType}.
     */
    public void registerDeletionService(DownloadsDeletionType type) {
        mDeletionType = type;
        mDeletionType.registerFreeableChangedListener(this);
    }

    /**
     * Registers a callback to be called when the amount of freeable space updates.
     * @param listener The callback listener.
     */
    public void registerFreeableChangedListener(DeletionType.FreeableChangedListener listener) {
        mListener = listener;
    }

    @Override
    public void onFreeableChanged(int numItems, long freeableBytes) {
        updatePreferenceText(numItems, freeableBytes, mDeletionType.getMostRecentLastModified());
        maybeUpdateListener(numItems, freeableBytes);
        updateFiles();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        boolean checked = (boolean) newValue;
        if (!checked) {
            // Temporarily stop listening to avoid propagating the checked change to children.
            setOnPreferenceChangeListener(null);
            setChecked(false);
            setOnPreferenceChangeListener(this);
        }

        // If we have no deletion type, we have no files to toggle.
        if (mDeletionType == null) {
            return true;
        }

        // If the group checkbox changed, we need to toggle every child preference.
        if (preference == this) {
            for (int i = 0; i < getPreferenceCount(); i++) {
                DownloadsFilePreference p = (DownloadsFilePreference) getPreference(i);
                p.setOnPreferenceChangeListener(null);
                mDeletionType.toggleFile(p.getFile(), checked);
                p.setChecked(checked);
                p.setOnPreferenceChangeListener(this);
            }
            maybeUpdateListener(mDeletionType.getFiles().size(), mDeletionType.getFreeableBytes());
            MetricsLogger.action(getContext(), MetricsEvent.ACTION_DELETION_SELECTION_DOWNLOADS,
                    checked);
            return true;
        }

        // If a single DownloadFilePreference changed, we need to toggle just itself.
        DownloadsFilePreference p = (DownloadsFilePreference) preference;
        mDeletionType.toggleFile(p.getFile(), checked);
        maybeUpdateListener(mDeletionType.getFiles().size(), mDeletionType.getFreeableBytes());
        return true;
    }

    @Override
    public void onClick() {
        super.onClick();
        MetricsLogger.action(getContext(), MetricsEvent.ACTION_DELETION_DOWNLOADS_COLLAPSED,
                isCollapsed());
    }

    private void updatePreferenceText(int itemCount, long bytes, long mostRecent) {
        Context context = getContext();
        setTitle(context.getString(R.string.deletion_helper_downloads_title, itemCount));
        // If there are no files to clear, show the empty text instead.
        if (itemCount != 0) {
            setSummary(context.getString(R.string.deletion_helper_downloads_category_summary,
                    Formatter.formatFileSize(context, bytes)));
        } else {
            setSummary(context.getString(R.string.deletion_helper_downloads_summary_empty,
                    Formatter.formatFileSize(context, bytes)));
        }
    }

    private void maybeUpdateListener(int numItems, long bytesFreeable) {
        if (mListener != null) {
            mListener.onFreeableChanged(numItems, bytesFreeable);
        }
    }

    private void updateFiles() {
        PreferenceListCache cache = new PreferenceListCache(this);
        Set<File> files = mDeletionType.getFiles();
        for (File file : files) {
            DownloadsFilePreference filePreference =
                    (DownloadsFilePreference) cache.getCachedPreference(file.getPath());
            if (filePreference == null) {
                filePreference = new DownloadsFilePreference(getContext(), file);
                filePreference.setChecked(mDeletionType.isChecked(file));
                filePreference.setOnPreferenceChangeListener(this);
            }
            addPreference(filePreference);
        }
        cache.removeCachedPrefs();
    }
}
