/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.plugin.devel.tasks.internal;

import com.google.common.reflect.TypeToken;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.properties.TypeScheme;
import org.gradle.api.internal.tasks.properties.annotations.OutputPropertyRoleAnnotationHandler;
import org.gradle.api.tasks.Nested;
import org.gradle.cache.internal.DefaultCrossBuildInMemoryCacheFactory;
import org.gradle.internal.event.DefaultListenerManager;
import org.gradle.internal.instantiation.generator.DefaultInstantiatorFactory;
import org.gradle.internal.properties.annotations.PropertyMetadata;
import org.gradle.internal.properties.annotations.TypeMetadata;
import org.gradle.internal.properties.annotations.TypeMetadataStore;
import org.gradle.internal.properties.annotations.TypeMetadataWalker;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.internal.service.DefaultServiceLocator;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistryBuilder;
import org.gradle.internal.service.scopes.PluginServiceRegistry;
import org.gradle.internal.service.scopes.Scope.Global;
import org.gradle.internal.state.DefaultManagedFactoryRegistry;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Supplier;

/**
 * Class for easy access to property validation from the validator task.
 */
@NonNullApi
public class PropertyValidationAccess {
    private static final PropertyValidationAccess INSTANCE = new PropertyValidationAccess();

    private final List<TypeScheme> typeSchemes;

    private PropertyValidationAccess() {
        ServiceRegistryBuilder builder = ServiceRegistryBuilder.builder().displayName("Global services");
        // Should reuse `GlobalScopeServices` here, however this requires a bunch of stuff in order to discover the plugin service registries
        // For now, re-implement the discovery here
        builder.provider(new Object() {
            @SuppressWarnings("unused")
            void configure(ServiceRegistration registration) {
                registration.add(DefaultListenerManager.class, new DefaultListenerManager(Global.class));
                registration.add(DefaultCrossBuildInMemoryCacheFactory.class);
                // TODO: do we need any factories here?
                registration.add(DefaultManagedFactoryRegistry.class, new DefaultManagedFactoryRegistry());
                registration.add(OutputPropertyRoleAnnotationHandler.class);
                registration.add(DefaultInstantiatorFactory.class);
                List<PluginServiceRegistry> pluginServiceFactories = new DefaultServiceLocator(false, getClass().getClassLoader()).getAll(PluginServiceRegistry.class);
                for (PluginServiceRegistry pluginServiceFactory : pluginServiceFactories) {
                    pluginServiceFactory.registerGlobalServices(registration);
                }
            }
        });
        ServiceRegistry services = builder.build();
        this.typeSchemes = services.getAll(TypeScheme.class);
    }

    @SuppressWarnings("unused")
    public static void collectValidationProblems(Class<?> topLevelBean, TypeValidationContext validationContext) {
        INSTANCE.collectTypeValidationProblems(topLevelBean, validationContext);
    }

    private void collectTypeValidationProblems(Class<?> topLevelBean, TypeValidationContext validationContext) {
        // Skip this for now
        if (topLevelBean.equals(TaskInternal.class)) {
            return;
        }

        TypeMetadataStore metadataStore = getTypeMetadataStore(topLevelBean);
        if (metadataStore == null) {
            // Don't know about this type
            return;
        }

        TypeToken<?> topLevelType = TypeToken.of(topLevelBean);
        TypeMetadataWalker.typeWalker(metadataStore, Nested.class).walk(topLevelType, new TypeMetadataWalker.NodeMetadataVisitor<TypeToken<?>>() {
            @Override
            public void visitRoot(TypeMetadata typeMetadata, TypeToken<?> value) {
                typeMetadata.visitValidationFailures(null, validationContext);
            }

            @Override
            public void visitNested(TypeMetadata typeMetadata, String qualifiedName, PropertyMetadata propertyMetadata, TypeToken<?> value) {
                typeMetadata.visitValidationFailures(qualifiedName, validationContext);
            }

            @Override
            public void visitLeaf(String qualifiedName, PropertyMetadata propertyMetadata, Supplier<TypeToken<?>> value) {
            }
        });
    }

    @Nullable
    private TypeMetadataStore getTypeMetadataStore(Class<?> topLevelBean) {
        for (TypeScheme typeScheme : typeSchemes) {
            if (typeScheme.appliesTo(topLevelBean)) {
                return typeScheme.getMetadataStore();
            }
        }
        return null;
    }
}
