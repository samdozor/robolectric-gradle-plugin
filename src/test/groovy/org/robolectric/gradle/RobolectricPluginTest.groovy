package org.robolectric.gradle

import org.junit.Test
import org.junit.Ignore
import org.gradle.api.Task
import org.gradle.api.Project
import org.gradle.api.internal.plugins.PluginApplicationException
import org.gradle.testfixtures.ProjectBuilder
import static org.fest.assertions.api.Assertions.*

class RobolectricPluginTest {

    @Test
    public void pluginDetectsLibraryPlugin() {
        Project project = ProjectBuilder.builder().build()
        project.apply plugin: 'com.android.library'
        project.apply plugin: 'robolectric'
    }

    @Test
    public void pluginDetectsExtendedLibraryPlugin() {
        Project project = ProjectBuilder.builder().build()
        project.apply plugin: 'extended-android-library'
        project.apply plugin: 'robolectric'
    }

    @Test(expected = PluginApplicationException.class)
    public void pluginFailsWithoutAndroidPlugin() {
        Project project = ProjectBuilder.builder().build()
        project.apply plugin: 'robolectric'
    }

    @Test
    public void pluginDetectsAppPlugin() {
        Project project = ProjectBuilder.builder().build()
        project.apply plugin: 'com.android.application'
        project.apply plugin: 'robolectric'
    }

    @Test
    public void pluginDetectsExtendedAppPlugin() {
        Project project = ProjectBuilder.builder().build()
        project.apply plugin: 'extended-android'
        project.apply plugin: 'robolectric'
    }

    @Test
    public void createsATestTaskForTheDebugVariant() {
        Project project = evaluatableProject()
        project.evaluate()

        assertThat(project.tasks.testDebug).isInstanceOf(org.gradle.api.tasks.testing.Test)
    }

    @Test
    public void createsATaskCompilingFilesInDefaultLocation() {
        Project project = evaluatableProject()
        project.evaluate()

        assertThat(project.tasks.compileTestDebugJava.source.files).containsOnly(project.file("src/androidTest/java/SomeTest.java"))
    }

    @Test
    public void createsATaskCompilingFilesInCustomLocation() {
        Project project = evaluatableProject()
        project.android.sourceSets.androidTest.java.srcDirs = ['customTestFolder/src']
        project.evaluate()

        assertThat(project.tasks.compileTestDebugJava.source.files).containsOnly(project.file("customTestFolder/src/SomeTest.java"))
    }

    @Test
    public void supportsAfterTestListenerForTheTestTask() {
        Project project = evaluatableProject()
        project.robolectric {
            afterTest { descriptor, result ->
                println "Executed ${descriptor.name} with result: ${result.resultType}"
            }
        }
        project.evaluate()

        assertThat(project.tasks.testDebug).isInstanceOf(org.gradle.api.tasks.testing.Test)
    }

    @Test
    public void supportsSettingAnExcludePattern_viaTheAndroidTestExtension() {
        Project project = evaluatableProject()
        project.robolectric { exclude "**/lame_tests/**" }
        project.evaluate()

        assertThat(project.tasks.testDebug.getExcludes().contains("**/lame_tests/**")).isTrue()
    }

    @Test
    public void supportsAddingJvmArgs_viaTheAndroidTestExtension() {
        Project project = evaluatableProject()
        project.robolectric { jvmArgs "-XX:TestArgument0", "-XX:TestArgument1" }
        project.evaluate()

        assertThat(project.tasks.testDebug.getJvmArgs()).contains("-XX:TestArgument0")
        assertThat(project.tasks.testDebug.getJvmArgs()).contains("-XX:TestArgument1")
    }

    @Test
    public void supportsMultipleIncludeAndExcludePatterns() {
        Project project = evaluatableProject()
        project.robolectric {
            exclude "**/lame_tests/**"
            exclude "**/lame_tests2/**", "**/lame_tests_3/**"
            include "**/robo_tests/**"
            include "**/robo_tests2/**", "**/robo_tests3/**"
        }
        project.evaluate()

        assertThat(project.tasks.testDebug.excludes).contains("**/lame_tests/**", "**/lame_tests2/**", "**/lame_tests_3/**")
        assertThat(project.tasks.testDebug.includes).contains("**/robo_tests/**", "**/robo_tests2/**", "**/robo_tests3/**")
    }

    @Test
    public void supportsIngoreFailures() {
        Project project = evaluatableProject()
        project.robolectric { ignoreFailures true }
        project.evaluate()

        assertThat(project.tasks.testDebug.ignoreFailures).isTrue()
    }

    @Test
    public void dumpsAllTestClassFilesAndResourcesIntoTheSameDirectory() {
        Project project = evaluatableProject()
        project.android { productFlavors { prod {}; beta {} } }
        project.evaluate()

        def expectedDestination = project.files("$project.buildDir/test-classes").singleFile
        assertThat(project.tasks.compileTestProdDebugJava.destinationDir).isEqualTo(expectedDestination)
        assertThat(project.tasks.compileTestBetaDebugJava.destinationDir).isEqualTo(expectedDestination)
        assertThat(project.tasks.processTestProdDebugResources.destinationDir).isEqualTo(expectedDestination)
        assertThat(project.tasks.processTestBetaDebugResources.destinationDir).isEqualTo(expectedDestination)
    }

    @Test @Ignore
    public void parseInstrumentTestCompile_androidGradle_0_13_0() {
        String androidGradleTool = "com.android.tools.build:gradle:0.13.0"
        String configurationName = "androidTestCompile"
        parseTestCompileDependencyWithAndroidGradle(androidGradleTool, configurationName)
    }

    private Project evaluatableProject() throws Exception {
        Project project = ProjectBuilder.builder().withProjectDir(new File("src/test/fixtures/android_app")).build();
        project.apply plugin: 'com.android.application'
        project.apply plugin: 'robolectric'
        project.android {
            compileSdkVersion 20
            buildToolsVersion '20.0.0'
        }
        return project
    }

    private void parseTestCompileDependencyWithAndroidGradle(String androidGradleTool, String configurationName) {
        Project project = ProjectBuilder.builder().build()
        project.buildscript {
            repositories {
                mavenCentral()
            }
            dependencies {
                classpath androidGradleTool
            }
        }
        project.repositories {
            mavenCentral()
        }

        project.apply plugin: 'com.android.application'
        project.apply plugin: 'robolectric'
        project.android {
            compileSdkVersion 20
            buildToolsVersion '20.0.0'
        }

        project.evaluate()
        project.dependencies.add(configurationName, 'junit:junit:4.8')

        Set<Task> testTaskSet = project.getTasksByName("test", false)
        assertThat(testTaskSet.size()).isEqualTo(1)

        Set<Task> compileTestDebugJavaTaskSet = project.getTasksByName("compileTestDebugJava", false)
        assertThat(compileTestDebugJavaTaskSet.size()).isEqualTo(1)

        Task compileDebugJavaTask = compileTestDebugJavaTaskSet.iterator().next()
        String filePathComponent = "junit" + File.separator + "junit" + File.separator + "4.8"
        boolean found = compileDebugJavaTask.classpath.getFiles().find { f ->
            f.toString().contains(filePathComponent)
        }
        assertThat(found).isTrue()
    }
}
