/*
 * Copyright (c) 2013 Nimbits Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either expressed or implied.  See the License for the specific language governing permissions and limitations under the License.
 */

package com.nimbits.cloudplatform.client.service.settings;


import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.rpc.RemoteService;
import com.google.gwt.user.client.rpc.RemoteServiceRelativePath;

import java.util.Map;

@RemoteServiceRelativePath("settingsService")
public interface SettingsService extends RemoteService {


    Map<String, String> getSettingsRpc() ;

    Map<String, String> getSettings();

    String getSetting(String paramName);

    void updateSetting(String setting, String newValue);

    void addSetting(String setting, String value);

    void addSetting(String setting, boolean defaultValue);

    static class App {
        private static SettingsServiceAsync ourInstance = GWT.create(SettingsService.class);

        public static synchronized SettingsServiceAsync getInstance() {
            return ourInstance;
        }
    }
}
