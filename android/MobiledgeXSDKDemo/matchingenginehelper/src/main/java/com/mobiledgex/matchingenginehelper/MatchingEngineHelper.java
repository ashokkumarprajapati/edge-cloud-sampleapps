/**
 * Copyright 2018-2022 MobiledgeX, Inc. All rights and licenses reserved.
 * MobiledgeX, Inc. 156 2nd Street #408, San Francisco, CA 94105
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mobiledgex.matchingenginehelper;

import static com.mobiledgex.matchingenginehelper.SettingsActivity.EXTRA_SHOW_FRAGMENT;
import static distributed_match_engine.AppClient.ServerEdgeEvent.ServerEventType.EVENT_APPINST_HEALTH;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Looper;
import android.telephony.SubscriptionInfo;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.eventbus.Subscribe;
import com.mobiledgex.matchingengine.ChannelIterator;
import com.mobiledgex.matchingengine.DmeDnsException;
import com.mobiledgex.matchingengine.EdgeEventsConnection;
import com.mobiledgex.matchingengine.MatchingEngine;
import com.mobiledgex.matchingengine.edgeeventsconfig.EdgeEventsConfig;
import com.mobiledgex.matchingengine.edgeeventsconfig.FindCloudletEvent;
import com.mobiledgex.matchingengine.edgeeventsconfig.FindCloudletEventTrigger;
import com.mobiledgex.matchingengine.edgeeventsconfig.UpdateConfig;
import com.mobiledgex.matchingengine.performancemetrics.NetTest;
import com.mobiledgex.matchingengine.performancemetrics.Site;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import distributed_match_engine.AppClient;
import distributed_match_engine.Appcommon;
import distributed_match_engine.LocOuterClass;
import io.grpc.StatusRuntimeException;

public class MatchingEngineHelper implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "MatchingEngineHelper";
    public static final boolean VERIFY_LOCATION_ENABLED = false;
    private Activity mActivity;
    private final View mView;
    private EdgeEventsSubscriber mEdgeEventsSubscriber;
    public int mDmePort = 50051;
    public static final String DEFAULT_DME_HOSTNAME = "eu-mexdemo.dme.mobiledgex.net"; //TODO: Change back to wifi whenever that gets fixed.
    public static final String DEFAULT_CARRIER_NAME = "";
    public static final String DEF_HOSTNAME_PLACEHOLDER = "Default";
    public static final String DEFAULT_FIND_CLOUDLET_MODE = "PROXIMITY";
    public static final int DEFAULT_APP_INSTANCES_LIMIT = 4;
    private String mDefaultCarrierName;
    private String mDefaultDmeHostname;
    public String mDmeHostname;
    private String mCarrierName;
    public String mAppName;
    public String mAppVersion;
    public String mOrgName;
    public MatchingEngine.FindCloudletMode mFindCloudletMode;
    private boolean mNetworkSwitchingAllowed;
    private boolean mUseWifiOnly;
    private boolean mMatchingEngineSSL;
    protected int mAppInstancesLimit;

    public Location mLastKnownLocation;
    public Location mLocationInSimulator;
    public String mSessionCookie;
    public AppClient.FindCloudletReply mClosestCloudlet;
    public AppClient.AppInstListReply mAppInstanceReplyList;
    public AppClient.QosPrioritySessionReply mQosPrioritySessionReply;
    public boolean mAllowLocationSimulatorUpdate = false;

    private final MatchingEngineHelperInterface meHelperInterface;
    private Location mSpoofedLocation = null;

    private MatchingEngine me;
    private EdgeEventsConfig mEdgeEventsConfig;
    private boolean mEdgeEventsRunning;
    private boolean mOverrideDefaultConfig;
    public static boolean mEdgeEventsEnabled = true;
    public String mQosSessionId = "";
    public String mQosProfileName = "";
    public String mAppInstIp;
    public String mDeviceIpv4;

    /**
     * Static variable to flag whether EdgeEvents should be restarted.
     * Set to false before opening Settings. Changing any Location Events Settings
     * or any Latency Events Settings will set it to true.
     */
    public static boolean mEdgeEventsConfigUpdated = false;
    public static boolean mAppDefinitionUpdated = false;

    private String someText = null;
    public String mAppInstHostname;
    public boolean mAppInstTls;
    private String mAppInstProto;
    private int mTestPort;
    private boolean mRunConnectionTests = true;

    private FusedLocationProviderClient mFusedLocationClient;
    private LocationRequest mLocationRequest;
    private boolean mGpsInitialized;

    // Key values for Edge Events.
    private final String prefKeyEnableEdgeEvents;
    private final String prefKeyOverrideDefaultConfig;
    private final String prefKeyLocationUpdatePattern;
    private final String prefKeyLocationUpdateInterval;
    private final String prefKeyLocationMaxNumUpdates;
    private final String prefKeyFindCloudletEventTrigger;
    private final String prefKeyLatencyUpdatePattern;
    private final String prefKeyLatencyTestType;
    private final String prefKeyLatencyTestTriggerMode;
    private final String prefKeyLatencyUpdateInterval;
    private final String prefKeyLatencyMaxNumUpdates;
    private final String prefKeyLatencyTestPort;
    private final String prefKeyLatencyThreshold;
    private final String prefKeyPerfMarginSwitch;
    private final String prefKeyAutoMigration;

    private final String prefKeyAllowMatchingEngineLocation;
    private final String prefKeyAllowNetSwitch;
    private final String prefKeyUseWifiOnly;
    private final String prefKeyMatchingEngineUseSsl;
    private final String prefKeyDmeHostname;
    private final String prefKeyOperatorName;
    private final String prefKeyDefaultDmeHostname;
    private final String prefKeyDefaultOperatorName;
    private final String prefKeyFindCloudletMode;
    private final String prefKeyAppInstancesLimit;
    private final String prefKeyDefaultAppInfo;
    private final String prefKeyAppName;
    private final String prefKeyAppVersion;
    private final String prefKeyOrgName;

    public static class Builder {
        private Activity activity;
        private View view;
        private MatchingEngineHelperInterface meHelperInterface;
        private int testPort;

        public Builder setActivity(Activity activity) {
            this.activity = activity;
            return this;
        }
        public Builder setMeHelperInterface(MatchingEngineHelperInterface meHelperInterface) {
            this.meHelperInterface = meHelperInterface;
            return this;
        }
        public Builder setView(View view) {
            this.view = view;
            return this;
        }
        public Builder setTestPort(int testPort) {
            this.testPort = testPort;
            return this;
        }

        public MatchingEngineHelper build() {
            return new MatchingEngineHelper(this);
        }
    }

    public MatchingEngineHelper(final Builder builder) {
        Log.i(TAG, "MatchingEngineHelper()");
        mActivity = builder.activity;
        mView = builder.view;
        meHelperInterface = builder.meHelperInterface;
        mTestPort = builder.testPort;
        me = new MatchingEngine(mActivity);

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(mActivity);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mActivity);
        Resources resources = mActivity.getResources();

        prefKeyAllowMatchingEngineLocation = resources.getString(R.string.pref_matching_engine_location_verification);
        prefKeyAllowNetSwitch = resources.getString(R.string.pref_net_switching_allowed);
        prefKeyUseWifiOnly = resources.getString(R.string.pref_use_wifi_only);
        prefKeyMatchingEngineUseSsl = resources.getString(R.string.pref_matching_engine_ssl);
        prefKeyDmeHostname = resources.getString(R.string.pref_dme_hostname);
        prefKeyOperatorName = resources.getString(R.string.pref_operator_name);
        prefKeyDefaultDmeHostname = resources.getString(R.string.pref_default_dme_hostname);
        prefKeyDefaultOperatorName = resources.getString(R.string.pref_default_operator_name);
        prefKeyFindCloudletMode = resources.getString(R.string.pref_find_cloudlet_mode);
        prefKeyAppInstancesLimit = resources.getString(R.string.pref_app_instances_limit);
        prefKeyDefaultAppInfo = resources.getString(R.string.pref_default_app_definition);
        prefKeyAppName = resources.getString(R.string.pref_app_name);
        prefKeyAppVersion = resources.getString(R.string.pref_app_version);
        prefKeyOrgName = resources.getString(R.string.pref_org_name);

        prefKeyEnableEdgeEvents = resources.getString(R.string.pref_enable_edge_events);
        prefKeyOverrideDefaultConfig = resources.getString(R.string.pref_override_default_config);
        prefKeyLocationUpdatePattern = resources.getString(R.string.pref_update_pattern_location);
        prefKeyLocationUpdateInterval = resources.getString(R.string.pref_update_interval_location);
        prefKeyLocationMaxNumUpdates = resources.getString(R.string.pref_max_num_updates_location);
        prefKeyFindCloudletEventTrigger = resources.getString(R.string.pref_FindCloudletEventTrigger);
        prefKeyLatencyTestType = resources.getString(R.string.pref_latency_test_type);
        prefKeyLatencyUpdatePattern = resources.getString(R.string.pref_update_pattern_latency);
        prefKeyLatencyTestTriggerMode = resources.getString(R.string.pref_latency_test_trigger_mode);
        prefKeyLatencyUpdateInterval = resources.getString(R.string.pref_update_interval_latency);
        prefKeyLatencyMaxNumUpdates = resources.getString(R.string.pref_max_num_updates_latency);
        prefKeyLatencyTestPort = resources.getString(R.string.pref_latency_test_port);
        prefKeyLatencyThreshold = resources.getString(R.string.pref_latency_threshold_ms);
        prefKeyPerfMarginSwitch = resources.getString(R.string.pref_perf_margin_switch);
        prefKeyAutoMigration = resources.getString(R.string.pref_automigration);

        // Reuse the onSharedPreferenceChanged code to initialize anything dependent on these prefs:
        onSharedPreferenceChanged(prefs, prefKeyAllowMatchingEngineLocation);
        onSharedPreferenceChanged(prefs, prefKeyDefaultDmeHostname);
        onSharedPreferenceChanged(prefs, prefKeyDefaultOperatorName);
        onSharedPreferenceChanged(prefs, prefKeyFindCloudletMode);
        onSharedPreferenceChanged(prefs, prefKeyAppInstancesLimit);
        onSharedPreferenceChanged(prefs, prefKeyAppName);
        onSharedPreferenceChanged(prefs, prefKeyAppVersion);
        onSharedPreferenceChanged(prefs, prefKeyOrgName);
        onSharedPreferenceChanged(prefs, prefKeyDefaultAppInfo);
        onSharedPreferenceChanged(prefs, prefKeyUseWifiOnly);
        onSharedPreferenceChanged(prefs, prefKeyMatchingEngineUseSsl);

        // Only initialize these on/off settings, but don't yet update the mEdgeEventsConfig.
        onEdgeEventPreferenceChanged(prefs, prefKeyEnableEdgeEvents);
        onEdgeEventPreferenceChanged(prefs, prefKeyOverrideDefaultConfig);

        Log.i(TAG, "mDmeHostname="+ mDmeHostname +" networkSwitchingAllowed="+mNetworkSwitchingAllowed+" mCarrierName="+getCarrierName());

        // Watch for any updated preferences:
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    /**
     * Initialize all Edge Events settings.
     */
    protected void initEdgeEventsConfigPrefs() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mActivity);
        onEdgeEventPreferenceChanged(prefs, prefKeyEnableEdgeEvents);
        onEdgeEventPreferenceChanged(prefs, prefKeyOverrideDefaultConfig);
        onEdgeEventPreferenceChanged(prefs, prefKeyLocationUpdatePattern);
        onEdgeEventPreferenceChanged(prefs, prefKeyLocationUpdateInterval);
        onEdgeEventPreferenceChanged(prefs, prefKeyLocationMaxNumUpdates);
        onEdgeEventPreferenceChanged(prefs, prefKeyFindCloudletEventTrigger);
        onEdgeEventPreferenceChanged(prefs, prefKeyLatencyTestType);
        onEdgeEventPreferenceChanged(prefs, prefKeyLatencyUpdatePattern);
        onEdgeEventPreferenceChanged(prefs, prefKeyLatencyTestTriggerMode);
        onEdgeEventPreferenceChanged(prefs, prefKeyLatencyUpdateInterval);
        onEdgeEventPreferenceChanged(prefs, prefKeyLatencyMaxNumUpdates);
        onEdgeEventPreferenceChanged(prefs, prefKeyLatencyTestPort);
        onEdgeEventPreferenceChanged(prefs, prefKeyLatencyThreshold);
        onEdgeEventPreferenceChanged(prefs, prefKeyPerfMarginSwitch);
        onEdgeEventPreferenceChanged(prefs, prefKeyAutoMigration);
    }

    public void startEdgeEvents() {
        if (!mEdgeEventsEnabled) {
            Log.e(TAG, "Aborting attempt to startEdgeEvents while mEdgeEventsEnabled="+ false);
            return;
        }
        if (mClosestCloudlet == null) {
            Log.w(TAG, "Must perform findCloudlet before starting Edge Events.");
            return;
        }

        new Thread(() -> {
            mEdgeEventsConfig = me.createDefaultEdgeEventsConfig();
            String message = "Created Default EdgeEventsConfig=" + mEdgeEventsConfig;
            Log.i(TAG, message);

            if (mOverrideDefaultConfig) {
                // In this case, all attributes for mEdgeEventsConfig will be set from Preferences.
                // Otherwise, we retain the default values.
                initEdgeEventsConfigPrefs();
                message = "Using Custom EdgeEventsConfig="+mEdgeEventsConfig;
                Log.i(TAG, message);
            }
            meHelperInterface.showMessage(message);

            // Only do this once.
            if (mEdgeEventsSubscriber == null) {
                mEdgeEventsSubscriber = new EdgeEventsSubscriber();
                me.getEdgeEventsBus().register(mEdgeEventsSubscriber);
            }

            if (mEdgeEventsRunning) {
                message = "Restarting ServerEdgeEvents";
                me.stopEdgeEvents();
            } else {
                message = "Subscribed to ServerEdgeEvents";
            }
            Log.i(TAG, message);
            meHelperInterface.showMessage(message);
            me.startEdgeEvents(mEdgeEventsConfig);
            mEdgeEventsRunning = true;
            mEdgeEventsConfigUpdated = false;
        }).start();
    }

    /**
     * This method performs several actions with the matching engine in the background,
     * one after the other:
     * <ol>
     *     <li>registerClient</li>
     *     <li>getAppInstList</li>
     *     <li>verifyLocation</li>
     *     <li>findCloudlet</li>
     * </ol>
     *
     */
    public void doEnhancedLocationUpdateInBackground() {
        new Thread(() -> {
            try {
                if (!validateCookie(mSessionCookie)) {
                    Log.e(TAG, "registerClient failed. aborting doEnhancedLocationUpdateInBackground");
                    return;
                }
                // Do all actions in order as long as each one is successful.
                if (!getAppInstList()) {
                    Log.e(TAG, "getAppInstList failed. aborting doEnhancedLocationUpdateInBackground");
                    return;
                }
                if (VERIFY_LOCATION_ENABLED) {
                    if (!verifyLocation()) {
                        Log.e(TAG, "verifyLocation failed. aborting doEnhancedLocationUpdateInBackground");
                        return;
                    }
                }
                if (!findCloudlet()) {
                    Log.e(TAG, "findCloudlet failed. aborting doEnhancedLocationUpdateInBackground");
                    return;
                }

            } catch (ExecutionException | InterruptedException
                    | PackageManager.NameNotFoundException | IOException e) {
                Log.e(TAG, "Exception in getAppInstListInBackground() for "+
                        mAppName+", "+mAppVersion+", "+mOrgName);
                e.printStackTrace();
                meHelperInterface.showError(e.getLocalizedMessage());
            }
        }).start();
    }

    private boolean verifyMeRequirements() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mActivity);
        boolean locationVerificationAllowed = prefs.getBoolean(mActivity.getResources().getString(R.string.pref_matching_engine_location_verification), false);
        if (!locationVerificationAllowed) {
            String msg = "Enhanced Location not enabled";
            Log.w(TAG, msg);
            Snackbar snackbar = Snackbar.make(mView, msg, Snackbar.LENGTH_INDEFINITE);
            snackbar.setAction("Settings", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(mActivity, SettingsActivity.class);
                    intent.putExtra(EXTRA_SHOW_FRAGMENT, SettingsActivity.MatchingEngineSettingsFragment.class.getName());
                    mActivity.startActivity(intent);
                }
            });
            snackbar.show();
            return false;
        }

        // In a real Edge-enabled app, if no carrierName, or active Subscription networks,
        // the app should use the public cloud instead.
        // For this demo app, we may want to test over wifi, which we can allow by disabling
        // the "Network Switching Enabled" preference.
        boolean netSwitchingAllowed = prefs.getBoolean(prefKeyAllowNetSwitch, false);
        Log.i(TAG, "prefKeyAllowNetSwitch="+netSwitchingAllowed+" isNetworkSwitchingEnabled()="+me.isNetworkSwitchingEnabled());
        if (me.isNetworkSwitchingEnabled()) {
            boolean carrierNameFound = false;
            List<SubscriptionInfo> subList = me.getActiveSubscriptionInfoList();
            if (subList != null && subList.size() > 0) {
                for (SubscriptionInfo info : subList) {
                    if (info.getCarrierName().length() > 0) {
                        carrierNameFound = true;
                    }
                }
            }
            if (!carrierNameFound) {
                String msg = "No valid SIM card. Disable \"Network Switching Enabled\" to continue.";
                Log.w(TAG, msg);
                meHelperInterface.showError(msg);
                Snackbar snackbar = Snackbar.make(mView, msg, Snackbar.LENGTH_LONG);
                snackbar.setAction("Settings", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(mActivity, SettingsActivity.class);
                        intent.putExtra(EXTRA_SHOW_FRAGMENT, SettingsActivity.MatchingEngineSettingsFragment.class.getName());
                        mActivity.startActivity(intent);
                    }
                });
                snackbar.show();
                return false;
            }
        }
        return true;
    }

    public boolean registerClient() throws ExecutionException, InterruptedException,
            StatusRuntimeException, PackageManager.NameNotFoundException {
        if (!verifyMeRequirements()) {
            String message = "Matching engine requirements not met. Please allow app permissions " +
                    "and enable Enhanced Location Verification in Matching Engine Settings";
            Log.e(TAG, message);
            meHelperInterface.showError(message);
            return false;
        }
        AppClient.RegisterClientRequest registerClientRequest;
        AppClient.RegisterClientReply registerReply;
        registerClientRequest = me.createDefaultRegisterClientRequest(mActivity, mOrgName)
                .setAppName(mAppName).setAppVers(mAppVersion).setCarrierName(getCarrierName()).build();
        registerReply = me.registerClient(registerClientRequest, mDmeHostname, mDmePort,10000);
        Log.i(TAG, "registerClientRequest="+registerClientRequest);
        Log.i(TAG, "registerClientRequest: "
                + " getAppName()=" + registerClientRequest.getAppName()
                + " getAppVers()=" + registerClientRequest.getAppVers()
                + " getOrgName()=" + registerClientRequest.getOrgName()
                + " getCarrierName()=" + registerClientRequest.getCarrierName());

        Log.i(TAG, "registerStatus.getStatus()="+registerReply.getStatus());

        if (registerReply.getStatus() != AppClient.ReplyStatus.RS_SUCCESS) {
            String registerStatusText = "registerClient Failed. Error: " + registerReply.getStatus();
            Log.e(TAG, registerStatusText);
            meHelperInterface.showError(registerStatusText);
            return false;
        }
        mSessionCookie = registerReply.getSessionCookie();
        Log.i(TAG, "mSessionCookie:" + mSessionCookie);
        meHelperInterface.onRegister();
        return true;
    }

    public void registerClientInBackground() {
        CompletableFuture.runAsync(() -> {
            try {
                registerClient();
            } catch (ExecutionException | InterruptedException | StatusRuntimeException
                    | PackageManager.NameNotFoundException | IllegalArgumentException e) {
                Log.e(TAG, "Exception in registerClientInBackground() for "+
                        mAppName+", "+mAppVersion+", "+mOrgName);
                e.printStackTrace();
                meHelperInterface.showError(e.getLocalizedMessage());
            }
        });
    }

    public boolean qosPrioritySessionCreate(String qosProfile, int duration) throws ExecutionException, InterruptedException,
            StatusRuntimeException, PackageManager.NameNotFoundException {
        String message;
        if (!verifyMeRequirements()) {
            message = "Matching engine requirements not met. Please allow app permissions " +
                    "and enable Enhanced Location Verification in Matching Engine Settings";
            Log.e(TAG, message);
            meHelperInterface.showError(message);
            return false;
        }
        if (mDeviceIpv4 == null || mDeviceIpv4.equals("") ||
                mAppInstIp == null || mAppInstIp.equals("") ||
                mAppInstProto == null || mAppInstProto.equals("") ||
                mTestPort == 0) {
            message = "Closest cloudlet uninitialized. Perform 'Find Closest Cloudlet'.";
            Log.e(TAG, message);
            meHelperInterface.showError(message);
            return false;
        }
        AppClient.QosPrioritySessionCreateRequest.Builder builder = me.createDefaultQosPrioritySessionCreateRequest(mActivity);
        builder.setPortApplicationServer(mTestPort+"");
        builder.setIpUserEquipment(mDeviceIpv4);
        builder.setIpApplicationServer(mAppInstIp);
        builder.setProtocolIn(AppClient.QosSessionProtocol.valueOf(mAppInstProto));
        builder.setProfile(AppClient.QosSessionProfile.valueOf(qosProfile));
        builder.setSessionDuration(duration);

        AppClient.QosPrioritySessionCreateRequest qosPrioritySessionCreateRequest;
        AppClient.QosPrioritySessionReply qosPrioritySessionReply;
        qosPrioritySessionCreateRequest = builder.build();
        mQosPrioritySessionReply = me.qosPrioritySessionCreate(qosPrioritySessionCreateRequest, mDmeHostname, mDmePort,10000);
        Log.i(TAG, "mQosPrioritySessionReply="+ mQosPrioritySessionReply);
        Log.i(TAG, "mQosPrioritySessionReply.getSessionId()="+ mQosPrioritySessionReply.getSessionId()+" mQosPrioritySessionReply.getHttpStatus()="+ mQosPrioritySessionReply.getHttpStatus());
        if (mQosPrioritySessionReply.getSessionId() == null || mQosPrioritySessionReply.getSessionId().length() == 0) {
            message = "qosPrioritySessionCreate Failed. HttpStatus: " + mQosPrioritySessionReply.getHttpStatus();
            Log.e(TAG, message);
            meHelperInterface.showError(message);
            return false;
        }
        mQosSessionId = mQosPrioritySessionReply.getSessionId();
        mQosProfileName = mQosPrioritySessionReply.getProfile().name();
        message = "Got session:\n"+getQosPrioritySessionDetails();
        Log.i(TAG, message);
        meHelperInterface.showMessage(message);
        return true;
    }

    public void qosPrioritySessionCreateInBackground(String qosProfile, int duration) {
        CompletableFuture.runAsync(() -> {
            try {
                qosPrioritySessionCreate(qosProfile, duration);
            } catch (ExecutionException | InterruptedException
                    | PackageManager.NameNotFoundException | IllegalArgumentException e) {
                Log.e(TAG, "Exception in qosPrioritySessionCreateInBackground() for "+
                        mAppName+", "+mAppVersion+", "+mOrgName);
                e.printStackTrace();
                meHelperInterface.showError(e.getLocalizedMessage());
            }
        });
    }

    public boolean qosPrioritySessionDelete() throws ExecutionException, InterruptedException,
            StatusRuntimeException, PackageManager.NameNotFoundException {
        if (!verifyMeRequirements()) {
            String message = "Matching engine requirements not met. Please allow app permissions " +
                    "and enable Enhanced Location Verification in Matching Engine Settings";
            Log.e(TAG, message);
            meHelperInterface.showError(message);
            return false;
        }
        if (mQosPrioritySessionReply == null) {
            String message = "QOS Priority session does not exist";
            Log.e(TAG, message);
            meHelperInterface.showError(message);
            return false;
        }
        AppClient.QosPrioritySessionDeleteRequest.Builder builder = me.createDefaultQosPrioritySessionDeleteRequest(mActivity);
        builder.setSessionId(mQosPrioritySessionReply.getSessionId());
        builder.setProfile(mQosPrioritySessionReply.getProfile());

        AppClient.QosPrioritySessionDeleteRequest qosPrioritySessionDeleteRequest;
        AppClient.QosPrioritySessionDeleteReply qosPrioritySessionDeleteReply;
        qosPrioritySessionDeleteRequest = builder.build();
        qosPrioritySessionDeleteReply = me.qosPrioritySessionDelete(qosPrioritySessionDeleteRequest, mDmeHostname, mDmePort,10000);
        Log.i(TAG, "qosPrioritySessionDeleteReply="+ qosPrioritySessionDeleteReply);
        Log.i(TAG, "qosPrioritySessionDeleteReply.getStatus()="+qosPrioritySessionDeleteReply.getStatus());
        String msg = "Deleted session "+mQosPrioritySessionReply.getSessionId();
        Log.i(TAG, msg);
        meHelperInterface.showMessage(msg);
        // Reset all QOS Session variables.
        mQosPrioritySessionReply = null;
        mQosSessionId = "";
        mQosProfileName = "";
        return true;
    }

    public void qosPrioritySessionDeleteInBackground() {
        CompletableFuture.runAsync(() -> {
            try {
                qosPrioritySessionDelete();
            } catch (ExecutionException | InterruptedException
                    | PackageManager.NameNotFoundException | IllegalArgumentException e) {
                Log.e(TAG, "Exception in registerClientInBackground() for "+
                        mAppName+", "+mAppVersion+", "+mOrgName);
                e.printStackTrace();
                meHelperInterface.showError(e.getLocalizedMessage());
            }
        });
    }

    /**
     * Depending on whether the session was created via FindCloudlet or qosPrioritySessionCreate,
     * there is a different amount of session details that can be displayed. This method returns
     * whatever details are available.
     *
     * @return Available session details.
     */
    public String getQosPrioritySessionDetails() {
        String details;
        if (mQosPrioritySessionReply == null) {
            if (mQosProfileName.equals("")) {
                details = "QOS Priority session does not exist";
            } else {
                details = "profile="+mQosProfileName+"\nsession_id="+mQosSessionId;
            }
        } else {
            // Don't worry. StringBuilder is used under the hood.
            details = "profile="+mQosPrioritySessionReply.getProfile();
            details += "\nsession_id="+mQosPrioritySessionReply.getSessionId();
            details += "\nhttp_status="+mQosPrioritySessionReply.getHttpStatus();
            details += "\nstarted_at="+mQosPrioritySessionReply.getStartedAt();
            details += "\nexpires_at="+mQosPrioritySessionReply.getExpiresAt();
            details += "\nsession_duration="+mQosPrioritySessionReply.getSessionDuration();
        }
        return details;
    }

    public void findCloudletInBackground() {
        CompletableFuture.runAsync(() -> {
            try {
                findCloudlet();
                mDeviceIpv4 = me.getLocalIpv4();
            } catch (ExecutionException | InterruptedException
                    | PackageManager.NameNotFoundException | IllegalArgumentException e) {
                Log.e(TAG, "Exception in findCloudletInBackground() for "+
                        mAppName+", "+mAppVersion+", "+mOrgName);
                e.printStackTrace();
                meHelperInterface.showError(e.getLocalizedMessage());
            }
        });
    }

    public void getAppInstListInBackground() {
        Log.i(TAG, "getAppInstListInBackground mAppName="+mAppName+" mAppVersion="+mAppVersion+" mOrgName="+mOrgName);

        meHelperInterface.showMessage("Get App Instances for AppName "+mAppName+" Version="+mAppVersion+" Org="+mOrgName+"...");
        new Thread(() -> {
            try {
                getAppInstList();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Exception in getAppInstListInBackground() for "+
                        mAppName+", "+mAppVersion+", "+mOrgName);
                e.printStackTrace();
                meHelperInterface.showError(e.getLocalizedMessage());
            }
        }).start();
    }

    public void verifyLocationInBackground() {
        new Thread(() -> {
            try {
                verifyLocation();
            } catch (ExecutionException | InterruptedException
                    | PackageManager.NameNotFoundException
                    | IllegalArgumentException | IOException e) {
                Log.e(TAG, "Exception in verifyLocationInBackground() for "+
                        mAppName+", "+mAppVersion+", "+mOrgName);
                e.printStackTrace();
                meHelperInterface.showError(e.getLocalizedMessage());
            }
        }).start();
    }

    public void doQosRequestInBackground(ArrayList<AppClient.QosPosition> kpiPositions) {
        new Thread(() -> {
            try {
                if (!validateCookie(mSessionCookie)) {
                    return;
                }
                getQosPositionKpi(kpiPositions);
            } catch (ExecutionException | InterruptedException
                    | PackageManager.NameNotFoundException | IllegalArgumentException e) {
                Log.e(TAG, "Exception in doQosRequestInBackground() for "+
                        mAppName+", "+mAppVersion+", "+mOrgName);
                e.printStackTrace();
                meHelperInterface.showError(e.getLocalizedMessage());
            }
        }).start();
    }

    public boolean findCloudlet() throws ExecutionException, InterruptedException,
            IllegalArgumentException, PackageManager.NameNotFoundException {
        mAppDefinitionUpdated = false;
        if (!validateCookie(mSessionCookie)) {
            return false;
        }
        AppClient.FindCloudletRequest findCloudletRequest;
        findCloudletRequest = me.createDefaultFindCloudletRequest(mActivity, getLocationForMatching()).setCarrierName(getCarrierName()).build();
        mClosestCloudlet = me.findCloudlet(findCloudletRequest,
                mDmeHostname, mDmePort,10000, mFindCloudletMode);

        Log.i(TAG, "findCloudlet mClosestCloudlet="+mClosestCloudlet);
        if(mClosestCloudlet.getStatus() != AppClient.FindCloudletReply.FindStatus.FIND_FOUND) {
            String findCloudletStatusText = "findCloudlet Failed. Error: " + mClosestCloudlet.getStatus();
            Log.e(TAG, findCloudletStatusText);
            meHelperInterface.showError(findCloudletStatusText);
            return false;
        }
        Log.i(TAG, "mClosestCloudlet.getFqdn()=" + mClosestCloudlet.getFqdn());
        Log.i(TAG, "Tags:"+mClosestCloudlet.getTagsMap());
        mQosSessionId = mClosestCloudlet.getTagsMap().get("priority_session_id");
        mQosProfileName = mClosestCloudlet.getTagsMap().get("qos_profile_name");
        meHelperInterface.showMessage("QosSessionId="+mQosSessionId+" QosProfileName="+mQosProfileName);

        //Find fqdnPrefix from Port structure.
        String fqdnPrefix = "";
        List<Appcommon.AppPort> ports = mClosestCloudlet.getPortsList();
        // Get data only from first port.
        Appcommon.AppPort aPort = ports.get(0);
        if (aPort != null) {
            Log.i(TAG, "Got port "+aPort+" TLS="+aPort.getTls()+" fqdnPrefix="+fqdnPrefix);
            fqdnPrefix = aPort.getFqdnPrefix();
            mAppInstTls = aPort.getTls();
            Log.i(TAG, "Setting TLS="+ mAppInstTls);
            if (aPort.getProto() == Appcommon.LProto.L_PROTO_UDP) {
                mAppInstProto = "UDP";
            }
            else if (aPort.getProto() == Appcommon.LProto.L_PROTO_TCP) {
                mAppInstProto = "TCP";
            }
        }
        // Build full hostname.
        mAppInstHostname = fqdnPrefix + mClosestCloudlet.getFqdn();
        try {
            InetAddress address = InetAddress.getByName(mAppInstHostname);
            mAppInstIp = address.getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
            mAppInstIp = "127.0.0.1"; // A valid IP address for testing.
        }

        meHelperInterface.showMessage("findCloudlet successful. Host="+mAppInstHostname+" IP="+mAppInstIp);
        onFindCloudlet(mClosestCloudlet);
        return true;
    }

    private void onFindCloudlet(AppClient.FindCloudletReply closestCloudlet) {
        if (closestCloudlet.equals(mClosestCloudlet)) {
            Log.i(TAG, "New onFindCloudlet with same closestCloudlet:" +closestCloudlet.getFqdn());
        }
        mClosestCloudlet = closestCloudlet;
        meHelperInterface.onFindCloudlet(closestCloudlet);
        if (mRunConnectionTests) {
            new Thread(() -> {
                ConnectionTester tester = meHelperInterface.makeConnectionTester(mAppInstTls);
                if (tester == null) {
                    // If the interface returns null, they don't want to do a connection test.
                    return;
                }
                if (tester.testConnection()) {
                    meHelperInterface.showMessage("Successfully connected to app inst on "+tester.mUrl);
                } else {
                    meHelperInterface.showError("Failed to connect to app inst on "+tester.mUrl);
                }
            }).start();
        }

        if (mEdgeEventsEnabled) {
            startEdgeEvents();
        }
    }

    public boolean getAppInstList() throws InterruptedException, ExecutionException, StatusRuntimeException {
        Log.i(TAG, "getAppInstList mAppName="+mAppName+" mAppVersion="+mAppVersion+" mOrgName="+mOrgName);
        mClosestCloudlet = null;
        try {
            if (!validateCookie(mSessionCookie)) {
                return false;
            }
        } catch (ExecutionException | InterruptedException | PackageManager.NameNotFoundException | StatusRuntimeException e) {
            Log.e(TAG, "Exception in getAppInstList() for "+
                    mAppName+", "+mAppVersion+", "+mOrgName);
            e.printStackTrace();
            meHelperInterface.showError(e.getLocalizedMessage());
            return false;
        }

        AppClient.AppInstListRequest appInstListRequest
                = me.createDefaultAppInstListRequest(mActivity, getLocationForMatching()).setCarrierName(getCarrierName()).setLimit(mAppInstancesLimit).build();
        if(appInstListRequest != null) {
            AppClient.AppInstListReply cloudletList = me.getAppInstList(appInstListRequest,
                    mDmeHostname, mDmePort, 10000);
            Log.i(TAG, "cloudletList.getStatus()="+cloudletList.getStatus());
            if (cloudletList.getStatus() != AppClient.AppInstListReply.AIStatus.AI_SUCCESS) {
                String message = "getAppInstList failed. Status="+cloudletList.getStatus();
                Log.e(TAG, message);
                meHelperInterface.showError(message);
                return false;
            }
            meHelperInterface.onGetCloudletList(cloudletList);
            return true;

        } else {
            String message = "Cannot create AppInstListRequest object";
            Log.e(TAG, message);
            meHelperInterface.showError(message);
            return false;
        }
    }

    private boolean verifyLocation() throws InterruptedException, IOException,
            ExecutionException, PackageManager.NameNotFoundException {
        if (!validateCookie(mSessionCookie)) {
            return false;
        }
        // Location Verification (Blocking, or use verifyLocationFuture):
        AppClient.VerifyLocationRequest verifyRequest =
                me.createDefaultVerifyLocationRequest(mActivity, getLocationForMatching())
                        .setCarrierName(getCarrierName()).build();
        if (verifyRequest != null) {
            AppClient.VerifyLocationReply verifiedLocation =
                    me.verifyLocation(verifyRequest, mDmeHostname, mDmePort, 10000);
            someText = "[Location Verified: Tower: " + verifiedLocation.getTowerStatus() +
                    ", GPS LocationStatus: " + verifiedLocation.getGpsLocationStatus() +
                    ", Location Accuracy: " + verifiedLocation.getGpsLocationAccuracyKm() + " ]\n";
            Log.i(TAG, someText);

            if (meHelperInterface != null) {
                meHelperInterface.onVerifyLocation(verifiedLocation.getGpsLocationStatus(),
                        verifiedLocation.getGpsLocationAccuracyKm());
            }
        } else {
            someText = "Cannot create VerifyLocationRequest object.\n";
            Log.e(TAG, someText);
            Snackbar.make(mView, someText, Snackbar.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    private void getQosPositionKpi(ArrayList<AppClient.QosPosition> positions) throws ExecutionException, InterruptedException {
        Log.i(TAG, "getQosPositionKpi me=" + me);
        AppClient.QosPositionRequest request = me.createDefaultQosPositionRequest(positions, 0, null).build();
        ChannelIterator<AppClient.QosPositionKpiReply> responseIterator = me.getQosPositionKpi(request,
                mDmeHostname, mDmePort, 10000);
        // A stream of QosPositionKpiReply(s), with a non-stream block of responses.
        long total = 0;
        while (responseIterator.hasNext()) {
            AppClient.QosPositionKpiReply aR = responseIterator.next();
            for (int i = 0; i < aR.getPositionResultsCount(); i++) {
                Log.i(TAG, aR.getPositionResults(i).toString());
            }
            total += aR.getPositionResultsCount();
        }
    }

    /**
     * Decodes the given cookie and if the expiration time has already passed,
     * performs another registerClient. Otherwise returns true;
     * @param cookie
     * @return
     * @throws InterruptedException
     * @throws ExecutionException
     * @throws PackageManager.NameNotFoundException
     */
    private boolean validateCookie(String cookie) throws InterruptedException, ExecutionException, PackageManager.NameNotFoundException, StatusRuntimeException {
        if (cookie != null) {
            // We already have a cookie. Decode and validate it.
            String[] parts = cookie.split("[.]");
            String header = parts[0];
            String body = parts[1];
            byte[] decodedBytes = Base64.decode(body, Base64.URL_SAFE);
            try {
                String jsonString = new String(decodedBytes, "UTF-8");
                Log.i(TAG, "jsonString="+jsonString);
                JSONObject jsonObject = new JSONObject(jsonString);
                long expTime = jsonObject.getLong("exp");
                Calendar calNow = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                Calendar calExp = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                calNow.setTimeInMillis(System.currentTimeMillis());
                calExp.setTimeInMillis(expTime*1000);
                int diff = calNow.compareTo(calExp);
                if (diff <= 0) {
                    Log.i(TAG, "Current cookie is valid");
                    return true;
                } else {
                    Log.w(TAG, "Current cookie has expired");
                    // Fall through and perform registerClient below.
                }
            } catch (UnsupportedEncodingException | JSONException e) {
                e.printStackTrace();
                return false;
            }
        }

        if (registerClient()) {
            // cookie is brand new, so known good.
            return true;
        } else {
            return false;
        }
    }

    public MatchingEngine getMatchingEngine() {
        return me;
    }

    public void setTestPort(int testPort) {
        Log.i(TAG, "setTestPort() Old="+mTestPort+" New="+testPort);
        mTestPort = testPort;
    }

    public void setMatchingEngine(MatchingEngine mMatchingEngine) {
        this.me = mMatchingEngine;
    }

    public Location getSpoofedLocation() {
        return mSpoofedLocation;
    }

    public void setSpoofedLocation(Location spoofedLocation) {
        Log.i(TAG, "setSpoofedLocation(" + spoofedLocation + ")");
        if (mClosestCloudlet != null) {
            postLocationToEdgeEvents(spoofedLocation);
        }
        mSpoofedLocation = spoofedLocation;
    }

    public void setSpoofedLocation(double latitude, double longitude) {
        Location location = new Location("MobiledgeX");
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        setSpoofedLocation(location);
    }

    public void onDestroy() {
        if (me != null) {
            me.close();
            me = null;
        }
        mEdgeEventsSubscriber = null;
        mActivity = null;
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences prefs, String key) {
        Log.d(TAG, "onSharedPreferenceChanged("+key+")");
        if (mActivity == null) {
            Log.e(TAG, "mActivity is null, possibly after onDestroy()");
            return;
        }

        if (key.equals(prefKeyAllowMatchingEngineLocation)) {
            boolean matchingEngineLocationAllowed = prefs.getBoolean(prefKeyAllowMatchingEngineLocation, false);
            Log.i(TAG, "onSharedPreferenceChanged("+key+")="+matchingEngineLocationAllowed);
            MatchingEngine.setMatchingEngineLocationAllowed(matchingEngineLocationAllowed);
            mAppDefinitionUpdated = true;
        }

        if (key.equals(prefKeyAllowNetSwitch)) {
            mNetworkSwitchingAllowed = prefs.getBoolean(prefKeyAllowNetSwitch, false);
            Log.i(TAG, "onSharedPreferenceChanged("+key+")="+mNetworkSwitchingAllowed);
            me.setNetworkSwitchingEnabled(mNetworkSwitchingAllowed);
        }

        if (key.equals(prefKeyUseWifiOnly)) {
            String carrierName = "";
            String message = "";
            mUseWifiOnly = prefs.getBoolean(key, false);
            Log.i(TAG, "onSharedPreferenceChanged("+key+")="+mUseWifiOnly);
            me.setUseWifiOnly(mUseWifiOnly);
            if (mUseWifiOnly) {
                carrierName = me.getCarrierName(mActivity);
            } else {
                // Get value from preferences.
                carrierName = prefs.getString(prefKeyOperatorName, DEFAULT_CARRIER_NAME);
            }
            message = "Wifi Only = "+me.isUseWifiOnly()+". Carrier Name changing from \"" + mCarrierName + "\" to \"" + carrierName + "\"";
            Log.i(TAG, message);
            if (!mCarrierName.equals(carrierName)) {
                // Don't show upon initial prefs reading.
                meHelperInterface.showMessage(message);
            }
            setCarrierName(carrierName);
        }

        if (key.equals(prefKeyMatchingEngineUseSsl)) {
            mMatchingEngineSSL = prefs.getBoolean(prefKeyMatchingEngineUseSsl, true);
            Log.i(TAG, "onSharedPreferenceChanged("+key+")="+mMatchingEngineSSL);
            me.setSSLEnabled(mMatchingEngineSSL);
        }

        if (key.equals(prefKeyDefaultDmeHostname)) {
            boolean useDefault = prefs.getBoolean(prefKeyDefaultDmeHostname, false);
            if (useDefault) {
                new Thread(() -> {
                    try {
                        mDefaultDmeHostname = me.generateDmeHostAddress();
                        String prefKeyValueDefaultDmeHostname = mActivity.getResources().getString(R.string.pref_value_default_dme_hostname);
                        prefs.edit().putString(prefKeyValueDefaultDmeHostname, mDefaultDmeHostname).apply();
                        setDmeHostname(mDefaultDmeHostname);
                    } catch (DmeDnsException e) {
                        mDefaultDmeHostname = DEFAULT_DME_HOSTNAME;
                        me.setUseWifiOnly(true);
                    }
                    Log.i(TAG, "mDefaultCarrierName="+mDefaultCarrierName+" mDefaultDmeHostname="+mDefaultDmeHostname);
                }).start();

            } else {
                // Change the key name so the normal DME hostname handling code will be used below.
                key = prefKeyDmeHostname;
            }
        }

        if (key.equals(prefKeyDefaultOperatorName)) {
            boolean useDefault = prefs.getBoolean(prefKeyDefaultOperatorName, false);
            if (useDefault) {
                // This will try to get the MccMnc from the device, unless wifiOnly is set.
                mDefaultCarrierName = me.getCarrierName(mActivity);
                Log.i(TAG, "mDefaultCarrierName=" + mDefaultCarrierName);
                String prefKeyValueDefaultOperatorName = mActivity.getResources().getString(R.string.pref_value_default_operator_name);
                prefs.edit().putString(prefKeyValueDefaultOperatorName, mDefaultCarrierName).apply();
                setCarrierName(mDefaultCarrierName);
            } else {
                // Change the key name so the normal Operator name handling code will be used below.
                key = prefKeyOperatorName;
            }
        }

        if (key.equals(prefKeyDmeHostname)) {
            String hostAndPort = prefs.getString(prefKeyDmeHostname, DEFAULT_DME_HOSTNAME+":"+"50051");
            Log.i(TAG, "onSharedPreferenceChanged("+key+")="+hostAndPort);
            String dmeHostname;

            try {
                dmeHostname = parseDmeHost(hostAndPort);
            } catch (HostParseException e) {
                String message = e.getLocalizedMessage()+" Default value will be used.";
                Log.e(TAG, message);
                Toast.makeText(mActivity, message, Toast.LENGTH_LONG).show();
                dmeHostname = DEFAULT_DME_HOSTNAME;
            }

            Log.i(TAG, "dmeHostname from preferences: "+dmeHostname);
            setDmeHostname(dmeHostname);
        }

        if (key.equals(prefKeyOperatorName)) {
            String carrierName = prefs.getString(prefKeyOperatorName, DEFAULT_CARRIER_NAME);
            Log.i(TAG, "onSharedPreferenceChanged("+key+")="+carrierName);
            setCarrierName(carrierName);
        }

        if (key.equals(prefKeyFindCloudletMode)) {
            String findCloudletMode = prefs.getString(prefKeyFindCloudletMode, DEFAULT_FIND_CLOUDLET_MODE);
            mFindCloudletMode = MatchingEngine.FindCloudletMode.valueOf(findCloudletMode);
            Log.i(TAG, "findCloudletMode="+findCloudletMode+" mFindCloudletMode="+mFindCloudletMode);
        }

        if (key.equals(prefKeyAppInstancesLimit)) {
            String appInstancesLimit = prefs.getString(key, ""+DEFAULT_APP_INSTANCES_LIMIT);
            try {
                mAppInstancesLimit = Integer.parseInt(appInstancesLimit);
            } catch (NumberFormatException e) {
                mAppInstancesLimit = DEFAULT_APP_INSTANCES_LIMIT;
            }
            Log.i(TAG, "appInstancesLimit="+appInstancesLimit+" mAppInstancesLimit="+mAppInstancesLimit);
            mAppDefinitionUpdated = true;
        }

        if (key.equals(prefKeyDefaultAppInfo)) {
            boolean useDefault = prefs.getBoolean(prefKeyDefaultAppInfo, true);
            if (useDefault) {
                mAppName = mActivity.getResources().getString(R.string.dme_app_name);
                mAppVersion = mActivity.getResources().getString(R.string.app_version);
                mOrgName = mActivity.getResources().getString(R.string.org_name);
            } else {
                mAppName = prefs.getString(prefKeyAppName, mActivity.getResources().getString(R.string.dme_app_name));
                mAppVersion = prefs.getString(prefKeyAppVersion, mActivity.getResources().getString(R.string.app_version));
                mOrgName = prefs.getString(prefKeyOrgName, mActivity.getResources().getString(R.string.org_name));
                Log.i(TAG, "onSharedPreferenceChanged("+key+")=false. Custom values: appName="+mAppName+" appVersion="+mAppVersion+" orgName="+mOrgName);
            }
            mAppDefinitionUpdated = true;
        }

        if (key.equals(prefKeyAppName)) {
            mAppName = prefs.getString(key, mActivity.getResources().getString(R.string.dme_app_name));
            Log.i(TAG, "onSharedPreferenceChanged("+key+")="+mAppName);
            mAppDefinitionUpdated = true;
        }
        if (key.equals(prefKeyAppVersion)) {
            mAppVersion = prefs.getString(key, mActivity.getResources().getString(R.string.app_version));
            Log.i(TAG, "onSharedPreferenceChanged("+key+")="+mAppVersion);
            mAppDefinitionUpdated = true;
        }
        if (key.equals(prefKeyOrgName)) {
            mOrgName = prefs.getString(key, mActivity.getResources().getString(R.string.org_name));
            Log.i(TAG, "onSharedPreferenceChanged("+key+")="+mOrgName);
            mAppDefinitionUpdated = true;
        }

        // Separated the Edge Event Settings into their own method.
        onEdgeEventPreferenceChanged(prefs, key);
    }

    /**
     * Handle any Edge Events settings updates. If the key is not an Edge Event key,
     * this method will simply fall through and do nothing.
     * @param prefs
     * @param key
     */
    protected void onEdgeEventPreferenceChanged(SharedPreferences prefs, String key) {
        Log.i(TAG, "onEdgeEventPreferenceChanged("+key+")");

        if (key.equals(prefKeyEnableEdgeEvents)) {
            mEdgeEventsConfigUpdated = true;
            boolean value = prefs.getBoolean(key, true);
            mEdgeEventsEnabled = value;
            String message;
            if (mEdgeEventsEnabled) {
                message = "Edge Events Enabled";
            } else {
                message = "Edge Events Disabled";
            }
            Log.i(TAG, message);
            meHelperInterface.showMessage(message);
            return;
        }
        if (key.equals(prefKeyOverrideDefaultConfig)) {
            mEdgeEventsConfigUpdated = true;
            boolean value = prefs.getBoolean(key, false);
            mOverrideDefaultConfig = value;
            String message;
            if (mOverrideDefaultConfig) {
                message = "Overriding Edge Events Default Config";
            } else {
                message = "Using Edge Events Default Config";
            }
            Log.i(TAG, message);
            meHelperInterface.showMessage(message);
            return;
        }

        if (mEdgeEventsConfig == null) {
            Log.i(TAG, "mEdgeEventsConfig not created yet.");
            return;
        }

        // Location Update Config
        if (key.equals(prefKeyLocationUpdatePattern)) {
            String pattern = prefs.getString(key, "onInterval");
            mEdgeEventsConfig.locationUpdateConfig.updatePattern = UpdateConfig.UpdatePattern.valueOf(pattern);
        }
        if (key.equals(prefKeyLocationUpdateInterval)) {
            int value = Integer.parseInt(prefs.getString(key, "30"));
            mEdgeEventsConfig.locationUpdateConfig.updateIntervalSeconds = value;
        }
        if (key.equals(prefKeyLocationMaxNumUpdates)) {
            int value = Integer.parseInt(prefs.getString(key, "0"));
            mEdgeEventsConfig.locationUpdateConfig.maxNumberOfUpdates = value;
        }

        // Latency Update Config
        if (key.equals(prefKeyLatencyUpdatePattern)) {
            String value = prefs.getString(key, "onInterval");
            mEdgeEventsConfig.latencyUpdateConfig.updatePattern = UpdateConfig.UpdatePattern.valueOf(value);
        }
        if (key.equals(prefKeyLatencyMaxNumUpdates)) {
            int value = Integer.parseInt(prefs.getString(key, "0"));
            mEdgeEventsConfig.latencyUpdateConfig.maxNumberOfUpdates = value;
        }
        if (key.equals(prefKeyLatencyTestPort)) {
            int value = Integer.parseInt(prefs.getString(key, "0"));
            mEdgeEventsConfig.latencyInternalPort = value;
        }

        // Edge Events Config
        if (key.equals(prefKeyFindCloudletEventTrigger)) {
            Set<String> stringSet = new HashSet<>();
            // Build default stringset with all possible values.
            stringSet.add("CloudletStateChanged");
            stringSet.add("AppInstHealthChanged");
            stringSet.add("CloudletMaintenanceStateChanged");
            stringSet.add("LatencyTooHigh");
            stringSet.add("CloserCloudlet");
            Set<String> values = prefs.getStringSet(key, stringSet);
            Log.i(TAG, "values="+values);
            EnumSet<FindCloudletEventTrigger> triggerSet = EnumSet.noneOf(FindCloudletEventTrigger.class);
            for (String s : values) {
                triggerSet.add(FindCloudletEventTrigger.valueOf(s));
            }
            mEdgeEventsConfig.triggers = triggerSet;
            mEdgeEventsConfigUpdated = true;
        }
        if (key.equals(prefKeyLatencyTestType)) {
            String value = prefs.getString(key, "CONNECT");
            mEdgeEventsConfig.latencyTestType = NetTest.TestType.valueOf(value);
        }
        if (key.equals(prefKeyLatencyTestTriggerMode)) {
            String value = prefs.getString(key, "PROXIMITY");
            mEdgeEventsConfig.latencyTriggerTestMode = MatchingEngine.FindCloudletMode.valueOf(value);
        }
        if (key.equals(prefKeyLatencyUpdateInterval)) {
            int value = Integer.parseInt(prefs.getString(key, "30"));
            mEdgeEventsConfig.latencyUpdateConfig.updateIntervalSeconds = value;
        }
        if (key.equals(prefKeyLatencyThreshold)) {
            int value = Integer.parseInt(prefs.getString(key, "0"));
            mEdgeEventsConfig.latencyThresholdTrigger = value;
        }
        if (key.equals(prefKeyPerfMarginSwitch)) {
            int value = prefs.getInt(key, 0);
            mEdgeEventsConfig.performanceSwitchMargin = value/100;
        }
        if (key.equals(prefKeyAutoMigration)) {
            boolean value = prefs.getBoolean(key, false);
            me.setAutoMigrateEdgeEventsConnection(value);
        }
    }

    public String getCarrierName() {
        if (me.isUseWifiOnly()) {
            return "";
        } else {
            return mCarrierName;
        }
    }

    public void setCarrierName(String carrierName) {
        Log.i(TAG, "setCarrierName("+carrierName+")");
        mSessionCookie = null;
        mCarrierName = carrierName;
        mClosestCloudlet = null;
        mAppDefinitionUpdated = true;
    }

    public void setDmeHostname(String hostname) {
        Log.i(TAG, "setDmeHostname("+hostname+")");
        meHelperInterface.showMessage("Using DME: "+hostname);
        mSessionCookie = null;
        mDmeHostname = hostname;
        mClosestCloudlet = null;
        if (VERIFY_LOCATION_ENABLED) {
            checkForLocSimulator(mDmeHostname);
        }
        mAppDefinitionUpdated = true;
    }

    public static String parseDmeHost(String hostAndPort) throws HostParseException {
        //Value is in this format: eu-mexdemo.dme.mobiledgex.net:50051
        String domainAndPortRegex = "^(((?!-)[A-Za-z0-9-]{1,63}(?<!-)\\.)+[A-Za-z]{2,6}):\\d+$";
        Pattern domainAndPortPattern = Pattern.compile(domainAndPortRegex);
        String ipAddressRegex = "^((?:[0-9]{1,3}\\.){3}[0-9]{1,3}):\\d+$";
        Pattern ipAddressPattern = Pattern.compile(ipAddressRegex);
        Matcher matcher1 = domainAndPortPattern.matcher(hostAndPort);
        Matcher matcher2 = ipAddressPattern.matcher(hostAndPort);
        if(matcher1.find()) {
            return matcher1.group(1);
        } else if (matcher2.find()) {
            return matcher2.group(1);
        } else {
            String message = "Invalid DME hostname and port: "+hostAndPort;
            throw new HostParseException(message);
        }
    }

    /**
     * Makes a request to the location simulator address for the corresponding DME hostname.
     * If the call is successful, we will allow location simulator updates in the UI.
     *
     * @param hostname  The DME hostname.
     */
    private void checkForLocSimulator(String hostname) {
        mAllowLocationSimulatorUpdate = false; // Default unless successful.
        // Instantiate the RequestQueue.
        RequestQueue queue = Volley.newRequestQueue(mActivity);
        String hostName = hostname.replace("dme", "locsim");
        String url = "http://"+hostName+":8888"; // Just check the index.
        Log.i(TAG, "checkForLocSimulator url="+url);

        // Request a string response from the provided URL.
        StringRequest stringRequest = new StringRequest(url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        Log.i(TAG, "checkForLocSimulator response="+response);
                        mAllowLocationSimulatorUpdate = true;
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e(TAG, "Non-200 response for checkForLocSimulator. error="+error+". " +
                        "This is OK and just means there's no location simulator for this DME.");
                mAllowLocationSimulatorUpdate = false;
            }
        });

        // Add the request to the RequestQueue.
        queue.add(stringRequest);
    }

    /**
     * If GPS spoof has been peformed, return that value, otherwise return the actual GPS location.
     * @return  location to be used for matching.
     */
    public Location getLocationForMatching() {
        if(mSpoofedLocation == null) {
            return mLastKnownLocation;
        } else {
            return mSpoofedLocation;
        }
    }

    /**
     * Post location into edgeEvents updater.
     *
     * @param location
     */
    public void postLocationToEdgeEvents(Location location) {
        Log.i(TAG, "postLocationToEdgeEvents("+location+")");
        if (!mEdgeEventsEnabled) {
            Log.i(TAG, "mEdgeEventsEnabled=false. Not posting location.");
            return;
        }

        if (location == null) {
            Log.i(TAG, "location=null. Not posting location.");
            return;
        }

        // Don't use shared thread which may have tasks queued. Create our own to post immediately.
        new Thread(() -> {
            Log.i(TAG, "Posting location to DME");
            DecimalFormat decFor = new DecimalFormat("#.#####");
            meHelperInterface.showMessage("Posting location to DME: "
                    + decFor.format(location.getLatitude()) + ", "
                    + decFor.format(location.getLongitude()));
            me.getEdgeEventsConnection().postLocationUpdate(location);
        }).start();
    }

    public String getClosestCloudletHostname() {
        if (mClosestCloudlet == null) {
            return null;
        } else {
            return mClosestCloudlet.getFqdn();
        }
    }

    // (Guava EventBus Interface)
    // This class encapsulates what an app might implement to watch for edge events. Not every event
    // needs to be implemented. If you just want FindCloudlet, just @Subscribe to FindCloudletEvent.
    //
    class EdgeEventsSubscriber {

        // Subscribe to error handlers!
        @Subscribe
        public void onMessageEvent(EdgeEventsConnection.EdgeEventsError error) {
            Log.d(TAG, "EdgeEvents error reported, reason: " + error);
            if (error.toString().equals("eventTriggeredButCurrentCloudletIsBest")) {
                return;
            }
            meHelperInterface.showError(error.toString());
        }

        // Subscribe to FindCloudletEvent updates to appInst. Reasons to do so include
        // the AppInst Health and Latency spec for this application.
        @Subscribe
        public void onMessageEvent(FindCloudletEvent findCloudletEvent) {
            Log.i(TAG, "Cloudlet update, reason: " + findCloudletEvent.trigger);

            // Connect to new Cloudlet in the event here, preferably in a background task.
            Log.i(TAG, "FindCloudletEvent. Cloudlet: " + findCloudletEvent.newCloudlet);

            someText = findCloudletEvent.newCloudlet.getFqdn();
            meHelperInterface.showMessage("FindCloudletEvent. FQDN: "+someText);

            Log.i(TAG, "Received: Server pushed a new FindCloudletReply to switch to: " + findCloudletEvent);
            handleFindCloudletServerPush(findCloudletEvent);
        }

        @Subscribe
        void handleEventLatencyResults(AppClient.ServerEdgeEvent event) {
            meHelperInterface.showMessage("Received: " + event.getEventType());
            LocOuterClass.Statistics s = event.getStatistics();
            DecimalFormat decFor = new DecimalFormat("#.##");
            String message = "Latency results: min/avg/max/stddev="
                    + decFor.format(s.getMin()) + "/" + decFor.format(s.getAvg()) + "/"
                    + decFor.format(s.getMax()) + "/" + decFor.format(s.getStdDev());
            meHelperInterface.showMessage(message);
        }

        void handleFindCloudletServerPush(FindCloudletEvent event) {
            meHelperInterface.showMessage("Received: FindCloudletEvent");

            // In this demo case, use our existing interface to display the newly selected cloudlet on the map.
            mClosestCloudlet = event.newCloudlet;
            onFindCloudlet(mClosestCloudlet);
        }

        void handleAppInstHealth(AppClient.ServerEdgeEvent event) {
            if (event.getEventType() != EVENT_APPINST_HEALTH) {
                return;
            }

            meHelperInterface.showMessage("Received: " + event.getEventType()
                    + event.getHealthCheck().name());

            switch (event.getHealthCheck()) {
                case HEALTH_CHECK_ROOTLB_OFFLINE:
                case HEALTH_CHECK_SERVER_FAIL:
                    doEnhancedLocationUpdateInBackground();
                    break;
                case HEALTH_CHECK_OK:
                    Log.i(TAG, "AppInst Health is OK");
                    break;
                case UNRECOGNIZED:
                    // fall through
                default:
                    Log.i(TAG, "AppInst Health event: " + event.getHealthCheck());
            }
        }

        void handleCloudletMaintenance(AppClient.ServerEdgeEvent event) {
            if (event.getEventType() != AppClient.ServerEdgeEvent.ServerEventType.EVENT_CLOUDLET_MAINTENANCE) {
                return;
            }

            meHelperInterface.showMessage("Received: " + event.getEventType()
                    + event.getMaintenanceState().name());

            switch (event.getMaintenanceState()) {
                case NORMAL_OPERATION:
                    Log.i(TAG, "Maintenance state is all good!");
                    break;
                default:
                    Log.i(TAG, "Server maintenance: " + event.getMaintenanceState());
            }
        }

        void handleCloudletState(AppClient.ServerEdgeEvent event) {
            if (event.getEventType() != AppClient.ServerEdgeEvent.ServerEventType.EVENT_CLOUDLET_STATE) {
                return;
            }

            meHelperInterface.showMessage("Received: " + event.getEventType()
                    + event.getCloudletState().name());

            switch (event.getCloudletState()) {
                case CLOUDLET_STATE_INIT:
                    Log.i(TAG, "Cloudlet is not ready yet. Wait or FindCloudlet again.");
                    break;
                case CLOUDLET_STATE_NOT_PRESENT:
                    Log.i(TAG, "Cloudlet is not present.");
                case CLOUDLET_STATE_UPGRADE:
                    Log.i(TAG, "Cloudlet is upgrading");
                case CLOUDLET_STATE_OFFLINE:
                    Log.i(TAG, "Cloudlet is offline");
                case CLOUDLET_STATE_ERRORS:
                    Log.i(TAG, "Cloudlet is offline");
                case CLOUDLET_STATE_READY:
                    // Timer Retry or just retry.
                    doEnhancedLocationUpdateInBackground();
                    break;
                case CLOUDLET_STATE_NEED_SYNC:
                    Log.i(TAG, "Cloudlet data needs to sync.");
                    break;
                default:
                    Log.i(TAG, "Not handled");
            }
        }

        // Only the app knows with any certainty which AppPort (and internal port array)
        // it wants to test, so this is in the application.
        @Subscribe
        void handleLatencyRequest(AppClient.ServerEdgeEvent event) {
            Log.i(TAG, "handleLatencyRequest event="+event);
            if (event.getEventType() != AppClient.ServerEdgeEvent.ServerEventType.EVENT_LATENCY_REQUEST) {
                return;
            }

            meHelperInterface.showMessage("Received: " + event.getEventType());

            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    // NetTest
                    // Local copy:
                    NetTest netTest = new NetTest();

                    // You must have performed FindCloudlet before you can do a NetTest.
                    if (mClosestCloudlet == null) {
                        meHelperInterface.showError("You must first perform Find Cloudlet.");
                        return;
                    }

                    // Assuming some knowledge of your own internal un-remapped server port
                    // discover, and test with the PerformanceMetrics API:
                    int publicPort;
                    HashMap<Integer, Appcommon.AppPort> ports = me.getAppConnectionManager().getTCPMap(mClosestCloudlet);
                    Appcommon.AppPort anAppPort = ports.get(mTestPort);
                    if (anAppPort == null) {
                        String message = "The expected port ("+mTestPort+") is not present. Aborting EVENT_LATENCY_REQUEST";
                        Log.e(TAG, message);
                        meHelperInterface.showError(message);
                        return;
                    }

                    // Test with default network in use:
                    publicPort = anAppPort.getPublicPort();
                    String host = me.getAppConnectionManager().getHost(mClosestCloudlet, anAppPort);
                    Site site = new Site(mActivity, mEdgeEventsConfig.latencyTestType, 5, host, publicPort);
                    netTest.addSite(site);
                    netTest.testSites(netTest.TestTimeoutMS); // Test the one we just added.

                    me.getEdgeEventsConnection().postLatencyUpdate(netTest.getSite(host),
                            getLocationForMatching());
                    meHelperInterface.showMessage("Latency results posted to DME: Avg="+site.average);
                }
            });
        }
    }

    /**
     * See documentation for Google's FusedLocationProviderClient for additional usage information.
     */
    public void startLocationUpdates() {
        Log.i(TAG, "startLocationUpdates()");

        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    int interval = 5000; // Initially, 5 second interval to get the first update quickly
                    Log.i(TAG, "mFusedLocationClient.getLastLocation()="+mFusedLocationClient.getLastLocation()+" interval="+interval);

                    mLocationRequest = new LocationRequest();
                    mLocationRequest.setSmallestDisplacement(5);
                    mLocationRequest.setInterval(interval);
                    mLocationRequest.setFastestInterval(interval);
                    mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);

                    mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
                    Log.i(TAG, "mFusedLocationClient.requestLocationUpdates() called");
                } catch (SecurityException se) {
                    se.printStackTrace();
                    Log.i(TAG, "App should Request location permissions during onResume().");
                }
            }
        });
    }

    public void stopLocationUpdates() {
        mFusedLocationClient.removeLocationUpdates(mLocationCallback);
    }

    LocationCallback mLocationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            List<Location> locationList = locationResult.getLocations();
            if (locationList.size() > 0) {
                //The last location in the list is the newest
                Location location = locationList.get(locationList.size() - 1);
                Log.i(TAG, "onLocationResult() Location: " + location.getLatitude() + " " + location.getLongitude());
                mLastKnownLocation = locationResult.getLastLocation();

                if (mClosestCloudlet != null) {
                    // If we are currently spoofing the GPS, don't post the actual location.
                    if (getSpoofedLocation() == null) {
                        Log.i(TAG, "Posting real location to DME");
                        postLocationToEdgeEvents(location);
                    }
                }

                if(!mGpsInitialized) {
                    meHelperInterface.getCloudlets(true, false);
                    mGpsInitialized = true;
                }

                if(mLocationRequest.getInterval() < 60000) {
                    // Now that we got our first update, make interval longer to save battery.
                    Log.i(TAG, "Slowing down location request interval");
                    mLocationRequest.setInterval(60000); // one minute interval
                    mLocationRequest.setFastestInterval(60000);
                }
            }
        }
    };
}
