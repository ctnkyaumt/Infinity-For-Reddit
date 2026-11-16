package ml.docilealligator.infinityforreddit.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.preference.Preference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.customviews.preference.CustomFontEditTextPreference;
import ml.docilealligator.infinityforreddit.customviews.preference.CustomFontPreference;
import ml.docilealligator.infinityforreddit.customviews.preference.CustomFontPreferenceFragmentCompat;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;

public class ApiSettingsPreferenceFragment extends CustomFontPreferenceFragmentCompat {
    private SharedPreferences mSharedPreferences;
    private CustomFontEditTextPreference mClientIdPref;
    private CustomFontEditTextPreference mRedirectUriPref;
    private CustomFontEditTextPreference mUserAgentPref;
    private CustomFontPreference mResetPref;

    private final SharedPreferences.OnSharedPreferenceChangeListener mPrefChangeListener = (prefs, key) -> {
        if (SharedPreferencesUtils.CUSTOM_CLIENT_ID.equals(key) ||
                SharedPreferencesUtils.CUSTOM_REDIRECT_URI.equals(key) ||
                SharedPreferencesUtils.CUSTOM_USER_AGENT.equals(key)) {
            APIUtils.init(prefs);
            updateSummaries();
        }
    };

    public ApiSettingsPreferenceFragment() {}

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.api_preferences, rootKey);

        mSharedPreferences = getPreferenceManager().getSharedPreferences();

        mClientIdPref = findPreference(SharedPreferencesUtils.CUSTOM_CLIENT_ID);
        mRedirectUriPref = findPreference(SharedPreferencesUtils.CUSTOM_REDIRECT_URI);
        mUserAgentPref = findPreference(SharedPreferencesUtils.CUSTOM_USER_AGENT);
        mResetPref = findPreference(SharedPreferencesUtils.RESET_API_SETTINGS);

        Preference.OnPreferenceChangeListener onPreferenceChangeListener = (preference, newValue) -> {
            String val = newValue != null ? ((String) newValue).trim() : "";
            mSharedPreferences.edit().putString(preference.getKey(), val).apply();
            if (preference instanceof CustomFontEditTextPreference) {
                ((CustomFontEditTextPreference) preference).setText(val);
            }
            APIUtils.init(mSharedPreferences);
            updateSummaries();
            return false;
        };

        if (mClientIdPref != null) {
            mClientIdPref.setOnPreferenceChangeListener(onPreferenceChangeListener);
        }
        if (mRedirectUriPref != null) {
            mRedirectUriPref.setOnPreferenceChangeListener(onPreferenceChangeListener);
        }
        if (mUserAgentPref != null) {
            mUserAgentPref.setOnPreferenceChangeListener(onPreferenceChangeListener);
        }

        if (mResetPref != null) {
            mResetPref.setOnPreferenceClickListener(preference -> {
                new MaterialAlertDialogBuilder(mActivity, R.style.MaterialAlertDialogTheme)
                        .setTitle(R.string.are_you_sure)
                        .setMessage(R.string.settings_reset_api_settings_summary)
                        .setPositiveButton(R.string.yes, (dialogInterface, i) -> {
                            mSharedPreferences.edit()
                                    .remove(SharedPreferencesUtils.CUSTOM_CLIENT_ID)
                                    .remove(SharedPreferencesUtils.CUSTOM_REDIRECT_URI)
                                    .remove(SharedPreferencesUtils.CUSTOM_USER_AGENT)
                                    .apply();

                            if (mClientIdPref != null) {
                                mClientIdPref.setText("");
                            }
                            if (mRedirectUriPref != null) {
                                mRedirectUriPref.setText("");
                            }
                            if (mUserAgentPref != null) {
                                mUserAgentPref.setText("");
                            }

                            APIUtils.init(mSharedPreferences);
                            updateSummaries();
                            Toast.makeText(mActivity, R.string.settings_api_settings_reset_toast, Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton(R.string.no, null)
                        .show();
                return true;
            });
        }

        updateSummaries();
    }

    private void updateSummaries() {
        if (mSharedPreferences == null) return;

        if (mClientIdPref != null) {
            String clientId = mSharedPreferences.getString(SharedPreferencesUtils.CUSTOM_CLIENT_ID, "");
            if (clientId != null && !clientId.trim().isEmpty()) {
                mClientIdPref.setSummary(clientId.trim());
            } else {
                mClientIdPref.setSummary(R.string.settings_client_id_summary);
            }
        }

        if (mRedirectUriPref != null) {
            String redirectUri = mSharedPreferences.getString(SharedPreferencesUtils.CUSTOM_REDIRECT_URI, "");
            if (redirectUri != null && !redirectUri.trim().isEmpty()) {
                mRedirectUriPref.setSummary(redirectUri.trim());
            } else {
                mRedirectUriPref.setSummary(R.string.settings_redirect_uri_summary);
            }
        }

        if (mUserAgentPref != null) {
            String userAgent = mSharedPreferences.getString(SharedPreferencesUtils.CUSTOM_USER_AGENT, "");
            if (userAgent != null && !userAgent.trim().isEmpty()) {
                mUserAgentPref.setSummary(userAgent.trim());
            } else {
                mUserAgentPref.setSummary(R.string.settings_user_agent_summary);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mSharedPreferences != null) {
            mSharedPreferences.registerOnSharedPreferenceChangeListener(mPrefChangeListener);
        }
        updateSummaries();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mSharedPreferences != null) {
            mSharedPreferences.unregisterOnSharedPreferenceChangeListener(mPrefChangeListener);
        }
    }
}
