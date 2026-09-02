/*
 * Copyright 2014 - Present Rafael Winterhalter
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
package net.bytebuddy.build.gradle;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskContainer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * A factory for tasks that uses the {@code org.gradle.api.tasks.TaskContainer#register} API if available. As the
 * created task is required during the configuration phase, the registered task is realized immediately.
 */
public class TaskFactory {

    /**
     * The dispatcher to use.
     */
    private static final Dispatcher DISPATCHER;

    /*
     * Resolves the dispatcher for the current Gradle version.
     */
    static {
        Dispatcher dispatcher;
        try {
            dispatcher = new Dispatcher.ForApi49CapableGradle(TaskContainer.class.getMethod("register", String.class, Class.class),
                    Class.forName("org.gradle.api.provider.Provider").getMethod("get"));
        } catch (Throwable ignored) {
            dispatcher = Dispatcher.ForLegacyGradle.INSTANCE;
        }
        DISPATCHER = dispatcher;
    }

    /**
     * A private constructor that is not supposed to be invoked.
     */
    private TaskFactory() {
        throw new UnsupportedOperationException("This class is a utility class and not supposed to be instantiated");
    }

    /**
     * Creates a task of the supplied type for the supplied project.
     *
     * @param project The Gradle project to use.
     * @param name    The name of the task to create.
     * @param type    The type of the task to create.
     * @param <T>     The type of the created task.
     * @return The created task.
     */
    public static <T extends Task> T create(Project project, String name, Class<T> type) {
        return type.cast(DISPATCHER.create(project.getTasks(), name, type));
    }

    /**
     * A dispatcher for using the {@code org.gradle.api.tasks.TaskContainer#register} API if available.
     */
    protected interface Dispatcher {

        /**
         * Creates a task of the supplied type within the supplied task container.
         *
         * @param tasks The task container to use.
         * @param name  The name of the task to create.
         * @param type  The type of the task to create.
         * @return The created task.
         */
        Task create(TaskContainer tasks, String name, Class<? extends Task> type);

        /**
         * A dispatcher for a legacy version of Gradle that does not support the task registration API.
         */
        enum ForLegacyGradle implements Dispatcher {

            /**
             * The singleton instance.
             */
            INSTANCE;

            /**
             * {@inheritDoc}
             */
            @SuppressWarnings("deprecation")
            public Task create(TaskContainer tasks, String name, Class<? extends Task> type) {
                return tasks.create(name, type);
            }
        }

        /**
         * A dispatcher for a Gradle version that supports the task registration API.
         */
        class ForApi49CapableGradle implements Dispatcher {

            /**
             * The {@code org.gradle.api.tasks.TaskContainer#register(String, Class)} method.
             */
            private final Method register;

            /**
             * The {@code org.gradle.api.provider.Provider#get()} method.
             */
            private final Method get;

            /**
             * Creates a new dispatcher.
             *
             * @param register The {@code org.gradle.api.tasks.TaskContainer#register(String, Class)} method.
             * @param get      The {@code org.gradle.api.provider.Provider#get()} method.
             */
            protected ForApi49CapableGradle(Method register, Method get) {
                this.register = register;
                this.get = get;
            }

            /**
             * {@inheritDoc}
             */
            public Task create(TaskContainer tasks, String name, Class<? extends Task> type) {
                try {
                    return (Task) get.invoke(register.invoke(tasks, name, type));
                } catch (IllegalAccessException exception) {
                    throw new IllegalStateException(exception);
                } catch (InvocationTargetException exception) {
                    Throwable cause = exception.getCause();
                    if (cause instanceof RuntimeException) {
                        throw (RuntimeException) cause;
                    } else if (cause instanceof Error) {
                        throw (Error) cause;
                    } else {
                        throw new RuntimeException(exception);
                    }
                }
            }
        }
    }
}
