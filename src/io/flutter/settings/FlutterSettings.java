/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.settings;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EventDispatcher;
import io.flutter.FlutterUtils;
import io.flutter.analytics.Analytics;
import io.flutter.bazel.Workspace;
import io.flutter.pub.PubRoot;
import io.flutter.pub.PubRoots;
import io.flutter.sdk.FlutterSdk;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EventListener;
import java.util.List;

public class FlutterSettings {
  private static final String reloadOnSaveKey = "io.flutter.reloadOnSave";
  private static final String openInspectorOnAppLaunchKey = "io.flutter.openInspectorOnAppLaunch";
  private static final String verboseLoggingKey = "io.flutter.verboseLogging";
  private static final String formatCodeOnSaveKey = "io.flutter.formatCodeOnSave";
  private static final String organizeImportsOnSaveKey = "io.flutter.organizeImportsOnSave";
  private static final String showOnlyWidgetsKey = "io.flutter.showOnlyWidgets";
  private static final String showPreviewAreaKey = "io.flutter.showPreviewArea";
  private static final String syncAndroidLibrariesKey = "io.flutter.syncAndroidLibraries";
  private static final String legacyTrackWidgetCreationKey = "io.flutter.trackWidgetCreation";
  private static final String disableTrackWidgetCreationKey = "io.flutter.disableTrackWidgetCreation";
  private static final String useFlutterLogView = "io.flutter.useLogView";
  private static final String memoryProfilerKey = "io.flutter.memoryProfiler";
  private static final String newBazelTestRunnerKey = "io.flutter.bazel.legacyTestBehavior";
  private static final String useBazelByDefaultKey = "io.flutter.bazel.useByDefault";

  public static FlutterSettings getInstance() {
    return ServiceManager.getService(FlutterSettings.class);
  }

  protected static PropertiesComponent getPropertiesComponent() {
    return PropertiesComponent.getInstance();
  }

  public interface Listener extends EventListener {
    void settingsChanged();
  }

  private final EventDispatcher<Listener> dispatcher = EventDispatcher.create(Listener.class);

  public FlutterSettings() {
    updateAnalysisServerArgs();
  }

  public void sendSettingsToAnalytics(Analytics analytics) {
    final PropertiesComponent properties = getPropertiesComponent();

    // Send data on the number of experimental features enabled by users.
    analytics.sendEvent("settings", "ping");

    if (isReloadOnSave()) {
      analytics.sendEvent("settings", afterLastPeriod(reloadOnSaveKey));
    }
    if (isOpenInspectorOnAppLaunch()) {
      analytics.sendEvent("settings", afterLastPeriod(openInspectorOnAppLaunchKey));
    }
    if (isFormatCodeOnSave()) {
      analytics.sendEvent("settings", afterLastPeriod(formatCodeOnSaveKey));

      if (isOrganizeImportsOnSaveKey()) {
        analytics.sendEvent("settings", afterLastPeriod(organizeImportsOnSaveKey));
      }
    }
    if (isShowOnlyWidgets()) {
      analytics.sendEvent("settings", afterLastPeriod(showOnlyWidgetsKey));
    }
    if (isShowPreviewArea()) {
      analytics.sendEvent("settings", afterLastPeriod(showPreviewAreaKey));
    }

    if (isSyncingAndroidLibraries()) {
      analytics.sendEvent("settings", afterLastPeriod(syncAndroidLibrariesKey));
    }
    if (isLegacyTrackWidgetCreation()) {
      analytics.sendEvent("settings", afterLastPeriod(legacyTrackWidgetCreationKey));
    }
    if (isDisableTrackWidgetCreation()) {
      analytics.sendEvent("settings", afterLastPeriod(disableTrackWidgetCreationKey));
    }
    if (useFlutterLogView()) {
      analytics.sendEvent("settings", afterLastPeriod(useFlutterLogView));
    }
    if (useBazelByDefault()) {
      analytics.sendEvent("settings", afterLastPeriod(useBazelByDefaultKey));
    }
  }

  public void addListener(Listener listener) {
    dispatcher.addListener(listener);
  }

  public void removeListener(Listener listener) {
    dispatcher.removeListener(listener);
  }

  public boolean isReloadOnSave() {
    return getPropertiesComponent().getBoolean(reloadOnSaveKey, true);
  }

  // TODO(jacobr): remove after 0.10.2 is the default.
  public boolean isLegacyTrackWidgetCreation() {
    return getPropertiesComponent().getBoolean(legacyTrackWidgetCreationKey, false);
  }

  public void setLegacyTrackWidgetCreation(boolean value) {
    getPropertiesComponent().setValue(legacyTrackWidgetCreationKey, value, false);

    fireEvent();
  }

  public boolean isTrackWidgetCreationEnabled(Project project) {
    final FlutterSdk flutterSdk = FlutterSdk.getFlutterSdk(project);
    if (flutterSdk != null && flutterSdk.getVersion().isTrackWidgetCreationRecommended()) {
      return !getPropertiesComponent().getBoolean(disableTrackWidgetCreationKey, false);
    }
    else {
      return isLegacyTrackWidgetCreation();
    }
  }

  public boolean isDisableTrackWidgetCreation() {
    return getPropertiesComponent().getBoolean(disableTrackWidgetCreationKey, false);
  }

  public void setDisableTrackWidgetCreation(boolean value) {
    getPropertiesComponent().setValue(disableTrackWidgetCreationKey, value, false);
    fireEvent();
  }

  public void setReloadOnSave(boolean value) {
    getPropertiesComponent().setValue(reloadOnSaveKey, value, true);

    fireEvent();
  }

  public boolean isFormatCodeOnSave() {
    return getPropertiesComponent().getBoolean(formatCodeOnSaveKey, false);
  }

  public void setFormatCodeOnSave(boolean value) {
    getPropertiesComponent().setValue(formatCodeOnSaveKey, value, false);

    fireEvent();
  }

  public boolean isOrganizeImportsOnSaveKey() {
    return getPropertiesComponent().getBoolean(organizeImportsOnSaveKey, false);
  }

  public void setOrganizeImportsOnSaveKey(boolean value) {
    getPropertiesComponent().setValue(organizeImportsOnSaveKey, value, false);

    fireEvent();
  }

  public boolean isShowOnlyWidgets() {
    return getPropertiesComponent().getBoolean(showOnlyWidgetsKey, false);
  }

  public void setShowOnlyWidgets(boolean value) {
    getPropertiesComponent().setValue(showOnlyWidgetsKey, value, false);

    fireEvent();
  }

  public boolean isShowPreviewArea() {
    return getPropertiesComponent().getBoolean(showPreviewAreaKey, false);
  }

  public void setShowPreviewArea(boolean value) {
    getPropertiesComponent().setValue(showPreviewAreaKey, value, false);

    fireEvent();
  }

  public boolean isSyncingAndroidLibraries() {
    return getPropertiesComponent().getBoolean(syncAndroidLibrariesKey, false);
  }

  public void setSyncingAndroidLibraries(boolean value) {
    getPropertiesComponent().setValue(syncAndroidLibrariesKey, value, false);

    fireEvent();
  }

  public boolean useFlutterLogView() {
    return getPropertiesComponent().getBoolean(useFlutterLogView, false);
  }

  public void setUseFlutterLogView(boolean value) {
    getPropertiesComponent().setValue(useFlutterLogView, value, false);

    fireEvent();
  }

  public boolean isOpenInspectorOnAppLaunch() {
    return getPropertiesComponent().getBoolean(openInspectorOnAppLaunchKey, false);
  }

  public void setOpenInspectorOnAppLaunch(boolean value) {
    getPropertiesComponent().setValue(openInspectorOnAppLaunchKey, value, false);

    fireEvent();
  }

  public boolean useBazelByDefault() {
    return getPropertiesComponent().getBoolean(useBazelByDefaultKey, false);
  }

  public void setUseBazelByDefault(boolean value) {
    getPropertiesComponent().setValue(useBazelByDefaultKey, value, false);

    fireEvent();
  }

  /**
   * Determines whether bazel should be used for a project based on the user's preferences and the structure of the project.
   */
  public boolean shouldUseBazel(Project project) {
    final boolean isBazelProject = FlutterModuleUtils.isFlutterBazelProject(project);
    final boolean isNotPubProject = PubRoots.forProject(project).isEmpty();
    return isBazelProject && (useBazelByDefault() || isNotPubProject);
  }

  // TODO(devoncarew): Remove this after M31 ships.
  private void updateAnalysisServerArgs() {
    final String serverRegistryKey = "dart.server.additional.arguments";
    final String previewDart2FlagSuffix = "preview-dart-2";

    final List<String> params = new ArrayList<>(StringUtil.split(Registry.stringValue(serverRegistryKey), " "));
    if (params.removeIf((s) -> s.endsWith(previewDart2FlagSuffix))) {
      Registry.get(serverRegistryKey).setValue(StringUtil.join(params, " "));
    }
  }

  public boolean isVerboseLogging() {
    return getPropertiesComponent().getBoolean(verboseLoggingKey, false);
  }

  public void setVerboseLogging(boolean value) {
    getPropertiesComponent().setValue(verboseLoggingKey, value, false);

    fireEvent();
  }

  public boolean isMemoryProfilerDisabled() {
    return getPropertiesComponent().getBoolean(memoryProfilerKey, false);
  }

  public void setMemoryProfilerDisabled(boolean value) {
    getPropertiesComponent().setValue(memoryProfilerKey, value, false);

    fireEvent();
  }

  protected void fireEvent() {
    dispatcher.getMulticaster().settingsChanged();
  }

  private static String afterLastPeriod(String str) {
    final int index = str.lastIndexOf('.');
    return index == -1 ? str : str.substring(index + 1);
  }
}
