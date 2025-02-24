/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.tasks.compile

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.file.TestFile
import org.gradle.internal.os.OperatingSystem
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.util.internal.TextUtil
import spock.lang.Issue

import static org.junit.Assume.assumeNotNull

class JavaCompileToolchainIntegrationTest extends AbstractIntegrationSpec implements JavaToolchainFixture {

    def setup() {
        file("src/main/java/Foo.java") << "public class Foo {}"
    }

    def "fails on toolchain and forkOptions mismatch when #when"() {
        def currentJdk = Jvm.current()
        def otherJdk = AvailableJavaHomes.differentVersion

        buildFile << """
            apply plugin: "java"
        """

        if (tool != null) {
            configureTool(tool == "current" ? currentJdk : otherJdk)
        }
        if (javaHome != null) {
            configureForkOptionsJavaHome(javaHome == "current" ? currentJdk : otherJdk)
        }
        if (executable != null) {
            configureForkOptionsExecutable(executable == "current" ? currentJdk : otherJdk)
        }

        when:
        withInstallations(currentJdk, otherJdk).runAndFail(":compileJava")

        then:
        failureDescriptionStartsWith("Execution failed for task ':compileJava'.")
        failureHasCause("Toolchain from `${errorFor}` property on `ForkOptions` does not match toolchain from `javaCompiler` property")

        where:
        when                                  | tool    | javaHome  | executable | errorFor
        "java home disagrees with executable" | null    | "other"   | "current"  | "executable"
        "tool disagrees with executable"      | "other" | null      | "current"  | "executable"
        "tool disagrees with java home"       | "other" | "current" | null       | "javaHome"
        "tool disagrees with "                | "other" | "current" | "current"  | "javaHome"
    }

    def "fails on toolchain and forkOptions mismatch when #when (without java base plugin)"() {
        def currentJdk = Jvm.current()
        def otherJdk = AvailableJavaHomes.differentVersion

        def compileWithVersion = [currentJdk, otherJdk].collect { it.javaVersion }.min()

        buildFile << """
            plugins {
                id 'jvm-toolchains'
            }

            task compileJava(type: JavaCompile) {
                classpath = project.layout.files()
                source = project.layout.files("src/main/java")
                destinationDirectory = project.layout.buildDirectory.dir("classes/java/main")
                sourceCompatibility = "${compileWithVersion}"
                targetCompatibility = "${compileWithVersion}"
            }
        """

        if (tool != null) {
            configureTool(tool == "current" ? currentJdk : otherJdk)
        }
        if (javaHome != null) {
            configureForkOptionsJavaHome(javaHome == "current" ? currentJdk : otherJdk)
        }
        if (executable != null) {
            configureForkOptionsExecutable(executable == "current" ? currentJdk : otherJdk)
        }

        when:
        withInstallations(currentJdk, otherJdk).runAndFail(":compileJava")

        then:
        failureDescriptionStartsWith("Execution failed for task ':compileJava'.")
        failureHasCause("Toolchain from `${errorForProperty}` property on `ForkOptions` does not match toolchain from `javaCompiler` property")

        where:
        when                                  | tool    | javaHome  | executable | errorForProperty
        "java home disagrees with executable" | null    | "other"   | "current"  | "executable"
        "tool disagrees with executable"      | "other" | null      | "current"  | "executable"
        "tool disagrees with java home"       | "other" | "current" | null       | "javaHome"
        "tool disagrees with "                | "other" | "current" | "current"  | "javaHome"
    }

    def "uses #what toolchain #when (with java plugin)"() {
        def currentJdk = Jvm.current()
        def otherJdk = AvailableJavaHomes.differentVersion
        def selectJdk = { it == "other" ? otherJdk : it == "current" ? currentJdk : null }

        buildFile << """
            apply plugin: "java"
        """

        if (withTool != null) {
            configureTool(selectJdk(withTool))
        }
        if (withJavaHome != null) {
            configureForkOptionsJavaHome(selectJdk(withJavaHome))
        }
        if (withExecutable != null) {
            configureForkOptionsExecutable(selectJdk(withExecutable))
        }
        if (withJavaExtension != null) {
            configureJavaPluginToolchainVersion(selectJdk(withJavaExtension))
        }

        def targetJdk = selectJdk(target)

        when:
        withInstallations(currentJdk, otherJdk).run(":compileJava", "--info")

        then:
        executedAndNotSkipped(":compileJava")
        outputContains("Compiling with toolchain '${targetJdk.javaHome.absolutePath}'")
        targetJdk.javaVersion == JavaVersion.forClass(javaClassFile("Foo.class").bytes)

        where:
        // Some cases are skipped, because forkOptions (when configured) must match the resulting toolchain, otherwise the build fails
        what             | when                         | withTool | withJavaHome | withExecutable | withJavaExtension | target
        "current JVM"    | "when nothing is configured" | null     | null         | null           | null              | "current"
        "java extension" | "when configured"            | null     | null         | null           | "other"           | "other"
        "executable"     | "when configured"            | null     | null         | "other"        | null              | "other"
        "java home"      | "when configured"            | null     | "other"      | null           | null              | "other"
        "assigned tool"  | "when configured"            | "other"  | null         | null           | null              | "other"
        "executable"     | "over java extension"        | null     | null         | "other"        | "current"         | "other"
        "java home"      | "over java extension"        | null     | "other"      | null           | "current"         | "other"
        "assigned tool"  | "over java extension"        | "other"  | null         | null           | "current"         | "other"
    }

    def "uses #what toolchain #when (without java base plugin)"() {
        def currentJdk = Jvm.current()
        def otherJdk = AvailableJavaHomes.differentVersion
        def selectJdk = { it == "other" ? otherJdk : it == "current" ? currentJdk : null }

        def compileWithVersion = [currentJdk, otherJdk].collect { it.javaVersion }.min()

        buildFile << """
            plugins {
                id 'jvm-toolchains'
            }

            task compileJava(type: JavaCompile) {
                classpath = project.layout.files()
                source = project.layout.files("src/main/java")
                destinationDirectory = project.layout.buildDirectory.dir("classes/java/main")
                sourceCompatibility = "${compileWithVersion}"
                targetCompatibility = "${compileWithVersion}"
            }
        """

        if (withTool != null) {
            configureTool(selectJdk(withTool))
        }
        if (withJavaHome != null) {
            configureForkOptionsJavaHome(selectJdk(withJavaHome))
        }
        if (withExecutable != null) {
            configureForkOptionsExecutable(selectJdk(withExecutable))
        }

        def targetJdk = selectJdk(target)

        when:
        withInstallations(currentJdk, otherJdk).run(":compileJava", "--info")

        then:
        executedAndNotSkipped(":compileJava")
        outputContains("Compiling with toolchain '${targetJdk.javaHome.absolutePath}'")

        where:
        // Some cases are skipped, because forkOptions (when configured) must match the resulting toolchain, otherwise the build fails
        what            | when                                 | withTool | withJavaHome | withExecutable | target
        "current JVM"   | "when toolchains are not configured" | null     | null         | null           | "current"
        "executable"    | "when configured"                    | null     | null         | "other"        | "other"
        "java home"     | "when configured"                    | null     | "other"      | null           | "other"
        "assigned tool" | "when configured"                    | "other"  | null         | null           | "other"
    }

    def "uses toolchain from forkOptions #forkOption when it points outside of installations"() {
        def currentJdk = Jvm.current()
        def otherJdk = AvailableJavaHomes.differentVersion

        def path = TextUtil.normaliseFileSeparators(otherJdk.javaHome.absolutePath.toString() + appendPath)

        def compatibilityVersion = [currentJdk, otherJdk].collect { it.javaVersion }.min()

        buildFile << """
            apply plugin: "java"

            compileJava {
                options.fork = true
                ${configure.replace("<path>", path)}
                sourceCompatibility = "${compatibilityVersion}"
                targetCompatibility = "${compatibilityVersion}"
            }
        """

        when:
        // not adding the other JDK to the installations
        withInstallations(currentJdk).run(":compileJava", "--info")

        then:
        executedAndNotSkipped(":compileJava")
        outputContains("Compiling with toolchain '${otherJdk.javaHome.absolutePath}'")
        JavaVersion.toVersion(compatibilityVersion) == JavaVersion.forClass(javaClassFile("Foo.class").bytes)

        where:
        forkOption   | configure                                       | appendPath
        "java home"  | 'options.forkOptions.javaHome = file("<path>")' | ''
        "executable" | 'options.forkOptions.executable = "<path>"'     | OperatingSystem.current().getExecutableName('/bin/javac')
    }

    @Issue("https://github.com/gradle/gradle/issues/22397")
    def "uses source and target compatibility from earlier toolchain from forkOptions #forkOption"() {
        def currentJdk = Jvm.current()
        def earlierJdk = AvailableJavaHomes.getDifferentVersion { it.languageVersion < currentJdk.javaVersion }
        assumeNotNull(earlierJdk)

        def path = TextUtil.normaliseFileSeparators(earlierJdk.javaHome.absolutePath.toString() + appendPath)

        buildFile << """
            apply plugin: "java"

            compileJava {
                options.fork = true
                ${configure.replace("<path>", path)}

                doFirst {
                    println "sourceCompatibility: \${sourceCompatibility}"
                    println "targetCompatibility: \${targetCompatibility}"
                }
            }
        """

        when:
        withInstallations(earlierJdk).run(":compileJava", "--info")

        then:
        executedAndNotSkipped(":compileJava")
        outputContains("Compiling with toolchain '${earlierJdk.javaHome.absolutePath}'")
        outputContains("sourceCompatibility: ${earlierJdk.javaVersion}")
        outputContains("targetCompatibility: ${earlierJdk.javaVersion}")
        earlierJdk.javaVersion == JavaVersion.forClass(javaClassFile("Foo.class").bytes)

        where:
        forkOption   | configure                                       | appendPath
        "java home"  | 'options.forkOptions.javaHome = file("<path>")' | ''
        "executable" | 'options.forkOptions.executable = "<path>"'     | OperatingSystem.current().getExecutableName('/bin/javac')
    }

    @Issue("https://github.com/gradle/gradle/issues/22398")
    def "ignore #forkOption if not forking"() {
        def curJvm = Jvm.current()
        def otherJvm = AvailableJavaHomes.getDifferentJdk()
        def path = TextUtil.normaliseFileSeparators(otherJvm.javaHome.absolutePath + appendPath)

        buildFile << """
            apply plugin: "java"

            compileJava {
                // we do not set `options.fork = true`
                ${configure.replace("<path>", path)}
            }
        """

        when:
        run(":compileJava", "--info")

        then:
        executedAndNotSkipped(":compileJava")
        outputContains("Compiling with toolchain '${curJvm.javaHome.absolutePath}'")
        outputContains("Compiling with JDK Java compiler API")
        outputDoesNotContain("Compiling with Java command line compiler")
        outputDoesNotContain("Started Gradle worker daemon")

        where:
        forkOption   | configure                                       | appendPath
        "java home"  | 'options.forkOptions.javaHome = file("<path>")' | ''
        "executable" | 'options.forkOptions.executable = "<path>"'     | OperatingSystem.current().getExecutableName('/bin/javac')
    }

    @ToBeFixedForConfigurationCache(because = "Creates a second exception")
    def 'fails when requesting not available toolchain'() {
        buildFile << """
            apply plugin: 'java'

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(99)
                }
            }
        """

        when:
        failure = executer
            .withToolchainDetectionEnabled()
            .withTasks("compileJava")
            .runWithFailure()

        then:
        failureHasCause('No compatible toolchains found for request specification: {languageVersion=99, vendor=any, implementation=vendor-specific} (auto-detect true, auto-download false)')
    }

    @Requires(adhoc = { AvailableJavaHomes.getJdk(JavaVersion.VERSION_1_7) != null })
    def "can use toolchains to compile java 1.7 code"() {
        def jdk = AvailableJavaHomes.getJdk(JavaVersion.VERSION_1_7)
        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(7)
                }
            }
        """

        when:
        withInstallations(jdk).run(":compileJava", "--info")

        then:
        outputContains("Compiling with Java command line compiler")
        outputContains("Compiling with toolchain '${jdk.javaHome.absolutePath}'.")
        javaClassFile("Foo.class").exists()
        jdk.javaVersion == JavaVersion.forClass(javaClassFile("Foo.class").bytes)
    }

    def "uses matching compatibility options for source and target level"() {
        def jdk = AvailableJavaHomes.getJdk(JavaVersion.VERSION_11)
        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(11)
                }
            }
        """

        file("src/main/java/Bar.java") << """
            public class Bar {
                public void bar() {
                    java.util.function.Function<String, String> append = (var string) -> string + " ";
                }
            }
        """

        when:
        withInstallations(jdk).run(":compileJava", "--info")

        then:
        outputContains("Compiling with toolchain '${jdk.javaHome.absolutePath}'.")
        javaClassFile("Bar.class").exists()
        jdk.javaVersion == JavaVersion.forClass(javaClassFile("Bar.class").bytes)
    }

    def "uses correct vendor when selecting a toolchain"() {
        def jdk = Jvm.current()

        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${jdk.javaVersion.majorVersion})
                    vendor = JvmVendorSpec.matching("${System.getProperty("java.vendor").toLowerCase()}")
                }
            }
        """

        when:
        withInstallations(jdk).run(":compileJava", "--info")

        then:
        outputContains("Compiling with toolchain '${jdk.javaHome.absolutePath}'.")
        javaClassFile("Foo.class").exists()
        jdk.javaVersion == JavaVersion.forClass(javaClassFile("Foo.class").bytes)
    }

    @ToBeFixedForConfigurationCache(because = "Creates a second exception")
    def "fails if no toolchain has a matching vendor"() {
        def version = Jvm.current().javaVersion.majorVersion
        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${version})
                    vendor = JvmVendorSpec.AMAZON
                }
            }
        """

        when:
        fails("compileJava")

        then:
        failureHasCause("No compatible toolchains found for request specification: {languageVersion=${version}, vendor=AMAZON, implementation=vendor-specific} (auto-detect false, auto-download false)")
    }

    def "can use compile daemon with tools jar"() {
        def jdk = AvailableJavaHomes.getJdk(JavaVersion.VERSION_1_8)
        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(8)
                }
            }
        """

        when:
        withInstallations(jdk).run(":compileJava", "--info")

        then:
        outputDoesNotContain("Compiling with Java command line compiler")
        outputContains("Compiling with toolchain '${jdk.javaHome.absolutePath}'.")
        javaClassFile("Foo.class").exists()
        jdk.javaVersion == JavaVersion.forClass(javaClassFile("Foo.class").bytes)
    }

    def 'configuring toolchain on java extension with source and target compatibility is supported'() {
        def jdk = Jvm.current()
        def prevJavaVersion = JavaVersion.toVersion(jdk.javaVersion.majorVersion.toInteger() - 1)
        buildFile << """
            apply plugin: 'java'

            java {
                sourceCompatibility = JavaVersion.toVersion('$prevJavaVersion')
                targetCompatibility = JavaVersion.toVersion('$prevJavaVersion')
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${jdk.javaVersion.majorVersion})
                }
            }

            compileJava {
                def projectSourceCompat = project.java.sourceCompatibility
                def projectTargetCompat = project.java.targetCompatibility
                doLast {
                    logger.lifecycle("project.sourceCompatibility = \$projectSourceCompat")
                    logger.lifecycle("project.targetCompatibility = \$projectTargetCompat")
                    logger.lifecycle("task.sourceCompatibility = \$sourceCompatibility")
                    logger.lifecycle("task.targetCompatibility = \$targetCompatibility")
                }
            }
        """

        when:
        withInstallations(jdk).run(":compileJava")

        then:
        outputContains("project.sourceCompatibility = $prevJavaVersion")
        outputContains("project.targetCompatibility = $prevJavaVersion")
        outputContains("task.sourceCompatibility = $prevJavaVersion")
        outputContains("task.targetCompatibility = $prevJavaVersion")
        prevJavaVersion == JavaVersion.forClass(javaClassFile("Foo.class").bytes)
    }

    def 'configuring toolchain on java extension and clearing source and target compatibility is supported'() {
        def jdk = Jvm.current()
        def javaVersion = jdk.javaVersion

        buildFile << """
            apply plugin: 'java'

            java {
                sourceCompatibility = JavaVersion.VERSION_14
                targetCompatibility = JavaVersion.VERSION_14
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${javaVersion.majorVersion})
                }
                sourceCompatibility = null
                targetCompatibility = null
            }

            compileJava {
                def projectSourceCompat = project.java.sourceCompatibility
                def projectTargetCompat = project.java.targetCompatibility
                doLast {
                    logger.lifecycle("project.sourceCompatibility = \$projectSourceCompat")
                    logger.lifecycle("project.targetCompatibility = \$projectTargetCompat")
                    logger.lifecycle("task.sourceCompatibility = \$sourceCompatibility")
                    logger.lifecycle("task.targetCompatibility = \$targetCompatibility")
                }
            }
        """

        when:
        withInstallations(jdk).run(":compileJava")

        then:
        outputContains("project.sourceCompatibility = $javaVersion")
        outputContains("project.targetCompatibility = $javaVersion")
        outputContains("task.sourceCompatibility = $javaVersion")
        outputContains("task.targetCompatibility = $javaVersion")
        javaVersion == JavaVersion.forClass(javaClassFile("Foo.class").bytes)
    }

    def 'source and target compatibility override toolchain (source #source, target #target)'() {
        def jdk11 = AvailableJavaHomes.getJdk(JavaVersion.VERSION_11)

        buildFile << """
            apply plugin: 'java'

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(11)
                }
            }

            compileJava {
                if ("$source" != 'none')
                    sourceCompatibility = JavaVersion.toVersion($source)
                if ("$target" != 'none')
                    targetCompatibility = JavaVersion.toVersion($target)
                def projectSourceCompat = project.java.sourceCompatibility
                def projectTargetCompat = project.java.targetCompatibility
                doLast {
                    logger.lifecycle("project.sourceCompatibility = \$projectSourceCompat")
                    logger.lifecycle("project.targetCompatibility = \$projectTargetCompat")
                    logger.lifecycle("task.sourceCompatibility = \$sourceCompatibility")
                    logger.lifecycle("task.targetCompatibility = \$targetCompatibility")
                }
            }
        """

        when:
        withInstallations(jdk11).run(":compileJava")

        then:
        outputContains("project.sourceCompatibility = 11")
        outputContains("project.targetCompatibility = 11")
        outputContains("task.sourceCompatibility = $sourceOut")
        outputContains("task.targetCompatibility = $targetOut")
        JavaVersion.toVersion(targetOut) == JavaVersion.forClass(javaClassFile("Foo.class").bytes)

        where:
        source | target | sourceOut | targetOut
        '9'    | '10'   | '9'       | '10'
        '9'    | 'none' | '9'       | '9'
        'none' | 'none' | '11'      | '11'
    }

    def "can compile Java using different JDKs"() {
        def jdk = AvailableJavaHomes.getJdk(javaVersion)
        assumeNotNull(jdk)

        buildFile << """
            plugins {
                id("java")
            }
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${jdk.javaVersion.majorVersion})
                }
            }
        """

        when:
        withInstallations(jdk).run(":compileJava", "--info")

        then:
        outputDoesNotContain("Compiling with Java command line compiler")
        outputContains("Compiling with toolchain '${jdk.javaHome.absolutePath}'.")
        javaClassFile("Foo.class").exists()
        jdk.javaVersion == JavaVersion.forClass(javaClassFile("Foo.class").bytes)

        where:
        javaVersion << JavaVersion.values().findAll { it.isJava8Compatible() }
    }

    /**
     * This test covers the case where in Java8 the class name becomes fully qualified in the deprecation message which is
     * somehow caused by invoking javacTask.getElements() in the IncrementalCompileTask of the incremental compiler plugin.
     */
    @Requires(TestPrecondition.JDK9_OR_LATER)
    def "Java deprecation messages with different JDKs"() {
        def jdk = AvailableJavaHomes.getJdk(javaVersion)

        buildFile << """
            plugins {
                id("java")
            }
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${jdk.javaVersion.majorVersion})
                }
            }
            tasks.withType(JavaCompile).configureEach {
                options.compilerArgs << "-Xlint:deprecation"
            }
        """

        file("src/main/java/com/example/Foo.java") << """
            package com.example;
            public class Foo {
                @Deprecated
                public void foo() {}
            }
        """

        def fileWithDeprecation = file("src/main/java/com/example/Bar.java") << """
            package com.example;
            public class Bar {
                public void bar() {
                    new Foo().foo();
                }
            }
        """

        //noinspection GrDeprecatedAPIUsage
        executer.expectDeprecationWarning("$fileWithDeprecation:5: warning: $deprecationMessage")

        when:
        withInstallations(jdk).run(":compileJava", "--info")

        then:
        outputDoesNotContain("Compiling with Java command line compiler")
        outputContains("Compiling with toolchain '${jdk.javaHome.absolutePath}'.")
        outputContains("Compiling with JDK Java compiler API.")
        javaClassFile("com/example/Foo.class").exists()
        javaClassFile("com/example/Bar.class").exists()

        where:
        javaVersion             | deprecationMessage
        JavaVersion.VERSION_1_8 | "[deprecation] foo() in com.example.Foo has been deprecated"
        JavaVersion.current()   | "[deprecation] foo() in Foo has been deprecated"
    }

    private TestFile configureForkOptionsExecutable(Jvm jdk) {
        buildFile << """
            compileJava {
                options.fork = true
                options.forkOptions.executable = "${TextUtil.normaliseFileSeparators(jdk.javacExecutable.absolutePath)}"
            }
        """
    }

    private TestFile configureForkOptionsJavaHome(Jvm jdk) {
        buildFile << """
            compileJava {
                options.fork = true
                options.forkOptions.javaHome = file("${TextUtil.normaliseFileSeparators(jdk.javaHome.absolutePath)}")
            }
        """
    }

    private TestFile configureTool(Jvm jdk) {
        buildFile << """
            compileJava {
                javaCompiler = javaToolchains.compilerFor {
                    languageVersion = JavaLanguageVersion.of(${jdk.javaVersion.majorVersion})
                }
            }
        """
    }
}
