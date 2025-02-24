/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.enterprise.impl;

import org.gradle.internal.enterprise.impl.legacy.DefaultBuildScanBuildStartedTime;
import org.gradle.internal.enterprise.impl.legacy.DefaultBuildScanClock;
import org.gradle.internal.enterprise.impl.legacy.DefaultBuildScanScopeIds;
import org.gradle.internal.enterprise.impl.legacy.LegacyGradleEnterprisePluginCheckInService;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry;
import org.gradle.internal.service.scopes.BuildScopeListenerManagerAction;

public class GradleEnterprisePluginServices extends AbstractPluginServiceRegistry {

    @Override
    public void registerBuildTreeServices(ServiceRegistration registration) {
        // legacy
        registration.add(DefaultBuildScanClock.class);
        registration.add(DefaultBuildScanBuildStartedTime.class);
        registration.add(GradleEnterpriseAutoAppliedPluginRegistry.class);
        registration.add(GradleEnterprisePluginAutoAppliedStatus.class);
    }

    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registration.add(GradleEnterprisePluginAutoApplicationListener.class);
        registration.add(GradleEnterprisePluginAutoApplicationListenerRegistrationAction.class);
    }

    public static class GradleEnterprisePluginAutoApplicationListenerRegistrationAction implements BuildScopeListenerManagerAction {
        private final GradleEnterprisePluginAutoApplicationListener listener;

        public GradleEnterprisePluginAutoApplicationListenerRegistrationAction(GradleEnterprisePluginAutoApplicationListener listener) {
            this.listener = listener;
        }

        @Override
        public void execute(ListenerManager listenerManager) {
            listenerManager.addListener(listener);
        }
    }

    @Override
    public void registerGradleServices(ServiceRegistration registration) {
        registration.add(DefaultGradleEnterprisePluginAdapter.class);
        registration.add(DefaultGradleEnterprisePluginBackgroundJobExecutors.class);
        registration.add(DefaultGradleEnterprisePluginBuildState.class);
        registration.add(DefaultGradleEnterprisePluginConfig.class);
        registration.add(DefautGradleEnterprisePluginCheckInService.class);
        registration.add(DefaultGradleEnterprisePluginRequiredServices.class);
        registration.add(DefaultGradleEnterprisePluginServiceRef.class);

        // legacy
        registration.add(DefaultBuildScanScopeIds.class);
        registration.add(LegacyGradleEnterprisePluginCheckInService.class);
    }

}
