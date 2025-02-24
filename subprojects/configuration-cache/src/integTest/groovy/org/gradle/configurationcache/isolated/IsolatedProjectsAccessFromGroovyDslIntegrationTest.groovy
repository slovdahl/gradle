/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.configurationcache.isolated

class IsolatedProjectsAccessFromGroovyDslIntegrationTest extends AbstractIsolatedProjectsIntegrationTest {
    def "reports problem when build script uses #block block to apply plugins to another project"() {
        settingsFile << """
            include("a")
            include("b")
        """
        buildFile << """
            $block {
                plugins.apply('java-library')
            }
        """

        when:
        configurationCacheFails("assemble")

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":a", ":b")
            problem("Build file 'build.gradle': Cannot access project ':a' from project ':'")
            problem("Build file 'build.gradle': Cannot access project ':b' from project ':'")
        }

        where:
        block         | _
        "allprojects" | _
        "subprojects" | _
    }

    def "reports problem when build script uses #block block to access dynamically added elements"() {
        settingsFile << """
            include("a")
            include("b")
        """
        buildFile << """
            $block {
                plugins.apply('java-library')
                java { }
                java.sourceCompatibility
            }
        """

        when:
        configurationCacheFails("assemble")

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":a", ":b")
            problem("Build file 'build.gradle': Cannot access project ':a' from project ':'", 3)
            problem("Build file 'build.gradle': Cannot access project ':b' from project ':'", 3)
        }

        where:
        block                               | _
        "allprojects"                       | _
        "subprojects"                       | _
        "configure(childProjects.values())" | _
    }

    def "reports problem when build script uses #property property to apply plugins to another project"() {
        settingsFile << """
            include("a")
            include("b")
        """
        buildFile << """
            ${property}.each {
                it.plugins.apply('java-library')
            }
        """

        when:
        configurationCacheFails("assemble")

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":a", ":b")
            problem("Build file 'build.gradle': Cannot access project ':a' from project ':'")
            problem("Build file 'build.gradle': Cannot access project ':b' from project ':'")
        }

        where:
        property                 | _
        "allprojects"            | _
        "subprojects"            | _
        "childProjects.values()" | _
    }

    def "reports problem when build script uses project() block to apply plugins to another project"() {
        settingsFile << """
            include("a")
            include("b")
        """
        buildFile << """
            project(':a') {
                plugins.apply('java-library')
            }
        """

        when:
        configurationCacheFails("assemble")

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":a", ":b")
            problem("Build file 'build.gradle': Cannot access project ':a' from project ':'")
        }
    }

    def "reports problem when root project build script uses #expression method to apply plugins to another project"() {
        settingsFile << """
            include("a")
            include("b")
        """
        buildFile << """
            ${expression}.plugins.apply('java-library')
        """

        when:
        configurationCacheFails("assemble")

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":a", ":b")
            problem("Build file 'build.gradle': Cannot access project ':a' from project ':'")
        }

        where:
        expression          | _
        "project(':a')"     | _
        "findProject(':a')" | _
    }

    def "reports problem when child project build script uses #expression method to apply plugins to sibling project"() {
        settingsFile << """
            include("a")
            include("b")
        """
        file("a/build.gradle") << """
            ${expression}.plugins.apply('java-library')
        """

        when:
        configurationCacheFails("assemble")

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":a", ":b")
            problem("Build file 'a/build.gradle': Cannot access project '$target' from project ':a'")
        }

        where:
        expression          | target
        "project(':b')"     | ":b"
        "findProject(':b')" | ":b"
        "rootProject"       | ":"
        "parent"            | ":"
    }

    def "reports problem when root project build script uses chain of methods #chain { } to apply plugins to other projects"() {
        settingsFile << """
            include("a")
            include("b")
        """
        buildFile << """
            $chain { it.plugins.apply('java-library') }
        """

        when:
        configurationCacheFails("assemble")

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":a", ":b")
            problem("Build file 'build.gradle': Cannot access project ':a' from project ':'")
            problem("Build file 'build.gradle': Cannot access project ':b' from project ':'")
        }

        where:
        chain                                           | _
        "project(':').allprojects"                      | _
        "project(':').subprojects"                      | _
        "project('b').project(':').allprojects"         | _
        "project('b').project(':').subprojects"         | _
        "project(':').allprojects.each"                 | _
        "project(':').subprojects.each"                 | _
        "project('b').project(':').allprojects.each"    | _
        "project('b').project(':').subprojects.each"    | _
        "findProject('b').findProject(':').subprojects" | _
    }

    def "reports problem when project build script uses chain of methods #chain { } to apply plugins to other projects"() {
        settingsFile << """
            include("a")
            include("b")
        """
        file("a/build.gradle") << """
            $chain { it.plugins.apply('java-library') }
        """

        when:
        configurationCacheFails("assemble")

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":a", ":b")
            problem("Build file 'a/build.gradle': Cannot access project ':b' from project ':a'")
        }

        where:
        chain                                    | _
        "project(':').subprojects"               | _
        "project(':').subprojects.each"          | _
        "rootProject.subprojects"                | _
        "parent.subprojects"                     | _
        "project(':b').project(':').subprojects" | _
        "project(':b').parent.subprojects"       | _
        "project(':').project('b')"              | _
        "findProject(':').findProject('b').with" | _
    }

    def "reports problem when project build script uses chain of methods #chain { } to apply plugins to all projects"() {
        settingsFile << """
            include("a")
            include("b")
        """
        file("a/build.gradle") << """
            $chain { it.plugins.apply('java-library') }
        """

        when:
        configurationCacheFails("assemble")

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":a", ":b")
            problem("Build file 'a/build.gradle': Cannot access project ':' from project ':a'")
            problem("Build file 'a/build.gradle': Cannot access project ':b' from project ':a'")
        }

        where:
        chain                           | _
        "project(':').allprojects"      | _
        "project(':').allprojects.each" | _
    }

    def "reports cross-project model access in Gradle.#invocation"() {
        settingsFile << """
            include("a")
            include("b")
        """
        file("a/build.gradle") << """
            configure(gradle) {
                ${invocation} { println(it.buildDir) }
            }
        """

        when:
        configurationCacheFails(":a:help", ":b:help")

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":a", ":b")
            accessedProjects.each {
                problem("Build file 'a/build.gradle': Cannot access project '$it' from project ':a'")
            }
        }

        where:
        invocation               | accessedProjects
        "configure(rootProject)" | [":"]
        "rootProject"            | [":"]
        "allprojects"            | [":", ":b"]
        "beforeProject"          | [":b"]
        "afterProject"           | [":b"]
    }

    def "reports cross-project model access in composite build access to Gradle.#invocation"() {
        settingsFile << """
            include("a")
            includeBuild("include")
        """
        file("include/build.gradle") << """
            gradle.${invocation}.allprojects { if (it.path == ":") println(it.buildDir) }
        """

        when:
        configurationCacheFails(":include:help")

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":include")
            problem("Build file 'include/build.gradle': Cannot access project ':' from project ':include'")
        }

        where:
        invocation | _
        "parent"   | _
        "root"     | _
    }

    def "reports cross-project model access from a listener added to Gradle.projectsEvaluated"() {
        settingsFile << """
            include("a")
            include("b")
        """
        file("a/build.gradle") << """
            gradle.projectsEvaluated {
                it.allprojects { println it.buildDir }
            }
        """

        when:
        configurationCacheFails(":a:help", ":b:help")

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":a", ":b")
            problem("Build file 'a/build.gradle': Cannot access project ':' from project ':a'")
            problem("Build file 'a/build.gradle': Cannot access project ':b' from project ':a'")
        }
    }

    def "reports cross-project model from ProjectEvaluationListener registered in Gradle.#invocation"() {
        settingsFile << """
            include("a")
            include("b")
        """
        file("a/build.gradle") << """
            class MyListener implements ProjectEvaluationListener {
                void beforeEvaluate(Project project) { }
                void afterEvaluate(Project project, ProjectState projectState) {
                    println project.buildDir
                }
            }
            gradle.$invocation(new MyListener())
        """

        when:
        configurationCacheFails(":a:help", ":b:help")

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":a", ":b")
            problem("Build file 'a/build.gradle': Cannot access project ':b' from project ':a'")
        }

        where:
        invocation                     | _
        "addListener"                  | _
        "addProjectEvaluationListener" | _
    }

    def "listener removal works properly in Gradle.#add + Gradle.#remove"() {
        settingsFile << """
            include("a")
            include("b")
        """
        file("a/build.gradle") << """
            class MyListener implements ProjectEvaluationListener {
                void beforeEvaluate(Project project) { }
                void afterEvaluate(Project project, ProjectState projectState) {
                    println project.buildDir
                }
            }
            def listener = new MyListener()
            gradle.$add(listener)
            gradle.$remove(listener)
        """

        when:
        configurationCacheRun(":a:help", ":b:help")

        then:
        fixture.assertStateStored {
            projectsConfigured(":", ":a", ":b")
        }

        where:
        add                            | remove
        "addListener"                  | "removeListener"
        "addProjectEvaluationListener" | "removeProjectEvaluationListener"
    }

    def "task graph should track cross-project model access in listeners with `#statement`"() {
        file("settings.gradle") << "include('a')"
        file("build.gradle") << """
            class MyListener implements TaskExecutionGraphListener {
                void graphPopulated(TaskExecutionGraph graph) {
                    graph.hasTask(":x:unknown")
                }
            }
            $statement
        """

        when:
        configurationCacheFails(":help", ":a:help")

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":a")
            problem("Build file 'build.gradle': Project ':' cannot access the tasks in the task graph that were created by other projects")
            failureCauseContains("Project ':' cannot access the tasks in the task graph that were created by other projects; tried to access ':x:unknown'")
        }

        where:
        statement                                                                                       | _
        "gradle.taskGraph.whenReady { graph -> graph.hasTask(':x:unknown') }"                           | _
        "gradle.taskGraph.addTaskExecutionGraphListener(new MyListener())"                              | _
    }

    def "checking cross-project model access in task graph call `#statement` with #tasksToRun, should succeed: #shouldSucceed"() {
        settingsFile << """
            include("b")
        """
        file("build.gradle") << """
            plugins {
                id("java")
            }
        """
        file("b/build.gradle") << """
            plugins {
                id("java")
            }
            dependencies {
                implementation(project(":"))
            }

            gradle.taskGraph.whenReady { graph ->
                $statement
            }
        """

        when:
        if (shouldSucceed) {
            configurationCacheRun(*tasksToRun)
        } else {
            configurationCacheFails(*tasksToRun)
        }

        then:
        if (shouldSucceed) {
            fixture.assertStateStored {
                projectsConfigured(":", ":b")
            }
        } else {
            fixture.assertStateStoredAndDiscarded {
                projectsConfigured(":", ":b")
                problem("Build file 'b/build.gradle': Project ':b' cannot access the tasks in the task graph that were created by other projects")
            }
        }

        where:
        statement                            | tasksToRun                       | shouldSucceed
        "graph.hasTask(':b:bTask')"          | [":b:help"]                      | true
        "graph.hasTask(':b:help')"           | [":b:help"]                      | true
        "graph.hasTask(':help')"             | [":b:help"]                      | false
        "graph.hasTask(':x:unknown')"        | [":b:help"]                      | false
        "graph.allTasks"                     | [":b:help"]                      | false
        "graph.allTasks"                     | [":b:help", ":help"]             | false
        "graph.getDependencies(help)"        | [":b:help"]                      | false
        "graph.getDependencies(compileJava)" | [":b:compileJava"]               | false
        "graph.filteredTasks"                | [":b:compileJava"]               | false
        "graph.filteredTasks"                | [":b:compileJava", "-x:classes"] | false
    }

    def "reports cross-project model access on #kind lookup in the parent project using `#expr`"() {
        settingsFile << """
            include("a")
        """
        file("build.gradle") << """
            $setExpr
        """
        file("a/build.gradle") << """
            println($expr)
        """

        when:
        configurationCacheFails(":a:help")

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":a")
            problem("Build file 'a/build.gradle': Project ':a' cannot dynamically look up a $kind in the parent project ':'")
        }

        where:
        kind       | setExpr         | expr
        "property" | "ext.foo = 1"   | "foo"
        "property" | "ext.foo = 1"   | "hasProperty('foo')"
        "property" | "ext.foo = 1"   | "property('foo')"
        "property" | "ext.foo = 1"   | "findProperty('foo')"
        "property" | "ext.foo = 1"   | "getProperty('foo')"
        "property" | "ext.foo = 1"   | "properties"
        "method"   | "def foo() { }" | "foo()"
    }

    def 'no duplicate problems reported for dynamic property lookup in transitive parents'() {
        settingsFile << """
            include(":sub")
            include(":sub:sub-a")
            include(":sub:sub-b")
        """
        buildFile << """
            ext.foo = "fooValue"
        """
        file("sub/sub-a/build.gradle") << """
            println(foo)
        """
        file("sub/sub-b/build.gradle") << """
            println(foo)
        """

        when:
        configurationCacheFails(":sub:sub-a:help", ":sub:sub-b:help")

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":sub", ":sub:sub-a", ":sub:sub-b")
            problem("Build file 'sub/sub-a/build.gradle': Project ':sub:sub-a' cannot dynamically look up a property in the parent project ':sub'")
            problem("Build file 'sub/sub-b/build.gradle': Project ':sub:sub-b' cannot dynamically look up a property in the parent project ':sub'")
        }
    }

    def 'user code in dynamic property lookup triggers a new isolation problem'() {
        settingsFile << """
            include(":sub")
            include(":sub:sub-sub")
        """
        buildFile << """
            ext.foo = "fooValue"
        """
        file("sub/build.gradle") << """
            abstract class Unusual {
                Project p
                @Inject Unusual(Project p) { this.p = p }
                Object getBar() {
                    // TODO: p.foo is not covered yet!
                    p.property("foo")
                }
            }

            // Convention plugin members are exposed as members of the project
            convention.plugins['unusual'] = objects.newInstance(Unusual)
        """
        file("sub/sub-sub/build.gradle") << """
            println(bar)
        """

        when:
        configurationCacheFails(":sub:sub-sub:help")

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":sub", ":sub:sub-sub")
            problem("Build file 'sub/sub-sub/build.gradle': Project ':sub' cannot dynamically look up a property in the parent project ':'")
            problem("Build file 'sub/sub-sub/build.gradle': Project ':sub:sub-sub' cannot dynamically look up a property in the parent project ':sub'")
        }
    }

    def "build script can query basic details of projects in allprojects block"() {
        settingsFile << """
            rootProject.name = "root"
            include("a")
            include("b")
        """
        buildFile << """
            plugins {
                id('java-library')
            }
            allprojects {
                println("project name = " + name)
                println("project path = " + path)
                println("project projectDir = " + projectDir)
                println("project rootDir = " + rootDir)
                it.name
                project.name
                project.path
                allprojects { }
            }
        """

        when:
        configurationCacheRun("assemble")

        then:
        fixture.assertStateStored {
            projectsConfigured(":", ":a", ":b")
        }
        outputContains("project name = root")
        outputContains("project name = a")
        outputContains("project name = b")
    }

    def "reports problem on #expr buildDependencies.getDependencies(...)"() {
        given:
        buildFile << """
            $setup
            def buildable = $expr
            configurations.create("test")
            println(buildable.buildDependencies.getDependencies(null))
        """

        when:
        configurationCacheFails(":help")

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":")
            problem("Build file 'build.gradle': Project ':' cannot access task dependencies directly")
        }

        where:
        expr                                                       | setup
        "files()"                                                  | ""
        "files() + files()"                                        | ""
        "fileTree(buildDir)"                                       | ""
        "fileTree(buildDir) + fileTree(rootDir)"                   | ""
        "resources.text.fromFile('1.txt', 'UTF-8')"                | ""
        "fromTask"                                                 | "def fromTask = new Object() { def buildDependencies = tasks.help.taskDependencies }"
        "artifacts.add('default', new File('a.txt'))"              | "configurations.create('default')"
        "dependencies.project([path: ':', configuration: 'test'])" | "plugins { id('java') }"
        "configurations.compileClasspath"                          | "plugins { id('java') }"
        "configurations.compileClasspath.dependencies"             | "plugins { id('java') }"
        "sourceSets.main.java"                                     | "plugins { id('java') }"
        "sourceSets.main.output"                                   | "plugins { id('java') }"
        "configurations.apiElements.allArtifacts"                  | "plugins { id('java') }"
        "configurations.apiElements.allArtifacts.toList()[0]"      | "plugins { id('java') }"
        "testing.suites.test"                                      | "plugins { id('java'); id('jvm-test-suite') }"
        "testing.suites.test.targets.toList()[0]"                  | "plugins { id('java'); id('jvm-test-suite') }"
        "publishing.publications.maven.artifacts.toList()[0]"      | "plugins { id('java'); id('maven-publish') }; publishing.publications.create('maven', MavenPublication) { from(components['java']) }"
    }

    def "mentions the specific project and build file in getDependencies(...) problems"() {
        given:
        settingsFile << """
            include(":a")
            include(":a:b")
        """
        file("a/b/build.gradle") << """
            def buildable = files()
            println(buildable.buildDependencies.getDependencies(null))
        """

        when:
        configurationCacheFails(":a:b:help")

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":a", ":a:b")
            problem("Build file 'a/b/build.gradle': Project ':a:b' cannot access task dependencies directly")
        }
    }

    def "project can access itself"() {
        settingsFile << """
            rootProject.name = "root"
            include("a")
            include("b")
        """
        buildFile << """
            rootProject.plugins.apply('java-library')
            project(':').plugins.apply('java-library')
            project(':a').parent.plugins.apply('java-library')
        """

        when:
        configurationCacheRun("assemble")

        then:
        fixture.assertStateStored {
            projectsConfigured(":", ":a", ":b")
        }
    }
}
