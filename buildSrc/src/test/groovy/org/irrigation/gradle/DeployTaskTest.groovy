package org.irrigation.gradle


import org.assertj.core.api.ObjectAssert
import org.gradle.testfixtures.ProjectBuilder
import org.hidetake.groovy.ssh.core.RunHandler
import org.hidetake.groovy.ssh.core.Service
import org.hidetake.groovy.ssh.session.Session
import org.hidetake.groovy.ssh.session.SessionHandler
import org.hidetake.groovy.ssh.util.Utility
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito

import java.nio.file.Paths

import static org.assertj.core.api.Assertions.assertThat
import static org.assertj.core.api.Assertions.assertThatThrownBy

class DeployTaskTest {
    def project = ProjectBuilder.builder().build()
    Service ssh

    @BeforeEach
    void setUp() {
        project.extensions.ssh = ssh = Mockito.mock(Service.class);
    }

    @Nested
    @DisplayName("All properties and arguments are correctly set up")
    class Configuration {

        @Test
        void useRemoteProperties() {
            def deployTask = project.task("deploy", type: DeployTask) as DeployTask
            registerProperties(host: "dev.irrigation.com", user: "root", identity_file: "id_rsa")

            def closure = runAndCaptureClosure {
                deployTask.deploy()
            }

            def runHandler = fillRunHandler(closure)
            def sessions = runHandler.getSessions()

            assertHasOneSession(sessions)
                .satisfies { session ->
                    assertThat(session.getRemote().getHost()).isEqualTo("dev.irrigation.com")
                    assertThat(session.getRemote().getUser()).isEqualTo("root")
                    assertThat(session.getRemote().getIdentity()).isEqualTo(Paths.get("id_rsa").toFile())
                }
        }

        @Test
        void missingUserProperty() {
            def deployTask = project.task("deploy", type: DeployTask) as DeployTask
            registerProperties(host: "dev.irrigation.com", identity_file: "id_rsa")

            assertThatThrownBy {
                runAndCaptureClosure {
                    deployTask.deploy()
                }
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Missing user")
            }

        }

        @Test
        void missingHostProperty() {
            def deployTask = project.task("deploy", type: DeployTask) as DeployTask
            registerProperties(user: "root", identity_file: "id_rsa")

            assertThatThrownBy {
                runAndCaptureClosure {
                    deployTask.deploy()
                }
            }
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Missing host")
        }

        @Test
        void useDefaultIdentity() {
            def deployTask = project.task("deploy", type: DeployTask) as DeployTask
            registerProperties(user: "root", host: "dev.irrigation.com")

            def closure = runAndCaptureClosure {
                deployTask.deploy()
            }

            def runHandler = fillRunHandler(closure)
            assertHasOneSession(runHandler.getSessions())
                .satisfies { session ->
                    assertThat(session.getRemote().getIdentity()).isEqualTo(Paths.get("${System.getProperty('user.home')}/.ssh/id_rsa").toFile())
                }
        }

        @Test
        void useDifferentEnvironment() {
            def deployTask = project.task("deploy", type: DeployTask) as DeployTask
            deployTask.configure {
                environment = "prod"
            }

            registerProperties("prod", user: "root", host: "prod.irrigation.com", identity_file: "prod_rsa")

            def closure = runAndCaptureClosure {
                deployTask.deploy()
            }

            def runHandler = fillRunHandler(closure)
            assertHasOneSession(runHandler.getSessions())
                .satisfies { session ->
                    assertThat(session.getRemote().getIdentity().toString()).isEqualTo("prod_rsa")
                    assertThat(session.getRemote().getHost()).isEqualTo("prod.irrigation.com")
                    assertThat(session.getRemote().getUser()).isEqualTo("root")
                }
        }

    }

    @DisplayName("All properties")
    @Nested
    class Running {
        @Test
        void executesCorrectCommands() {
            def deployTask = project.task("deploy", type: DeployTask) as DeployTask
            registerProperties(user: "root", host: "dev.irrigation.com")

            def closure = runAndCaptureClosure {
                deployTask.deploy()
            }

            def runHandler = fillRunHandler(closure)
            def sessions = runHandler.getSessions()

            assertHasOneSession(sessions)
                .satisfies { session ->

                    def capturedCommands = captureCommands(session)
                    def executions = capturedCommands.executeArguments;

                    assertThat(executions)
                        .containsExactly(
                            "cd ~; if [ -d irrigation ] && [ -f irrigation/docker-compose.yml ]; then cd irrigation; docker-compose down; fi; cd ~; if [ -d irrigation_local ] && [ -f irrigation_local/docker-compose.yml ]; then cd irrigation_local; docker-compose down; fi;",
                            "cd ~; if [ ! -d irrigation ]; then git clone https://github.com/aykborstelmann/irrigation.git irrigation; fi;",
                            "cd ~/irrigation/; git fetch -p; if [ -z \$(git branch --list master) ] ; then git checkout -b master origin/master; else git checkout master; fi; git reset --hard origin/master",
                            "cd ~/irrigation/; chmod +x gradlew && ./gradlew docker && docker-compose up -d"
                        )
                }


        }

        @Test
        void usesBranchCorrectly() {
            def deployTask = project.task("deploy", type: DeployTask) as DeployTask
            deployTask.configure {
                branch = "feature/test"
            }
            registerProperties(user: "root", host: "dev.irrigation.com")

            def closure = runAndCaptureClosure {
                deployTask.deploy()
            }

            def runHandler = fillRunHandler(closure)
            def sessions = runHandler.getSessions()

            assertHasOneSession(sessions)
                .satisfies { session ->
                    def capturedCommands = captureCommands(session)
                    List<String> executions = capturedCommands.executeArguments;

                    assertThat(executions)
                        .containsExactly(
                            "cd ~; if [ -d irrigation ] && [ -f irrigation/docker-compose.yml ]; then cd irrigation; docker-compose down; fi; cd ~; if [ -d irrigation_local ] && [ -f irrigation_local/docker-compose.yml ]; then cd irrigation_local; docker-compose down; fi;",
                            "cd ~; if [ ! -d irrigation ]; then git clone https://github.com/aykborstelmann/irrigation.git irrigation; fi;",
                            "cd ~/irrigation/; git fetch -p; if [ -z \$(git branch --list feature/test) ] ; then git checkout -b feature/test origin/feature/test; else git checkout feature/test; fi; git reset --hard origin/feature/test",
                            "cd ~/irrigation/; chmod +x gradlew && ./gradlew docker && docker-compose up -d"
                        )
                }


        }

        @Test
        void useLocalCopy() {
            def deployTask = project.task("deploy", type: DeployTask) as DeployTask
            deployTask.configure {
                local = true
            }
            registerProperties(user: "root", host: "dev.irrigation.com")

            def closure = runAndCaptureClosure {
                deployTask.deploy()
            }

            def runHandler = fillRunHandler(closure)
            def sessions = runHandler.getSessions()

            assertHasOneSession(sessions)
                .satisfies { session ->
                    def capturedCommands = captureCommands(session)
                    def executions = capturedCommands.executeArguments;
                    List<HashMap> puts = capturedCommands.putArguments;

                    assertThat(executions)
                        .containsExactly(
                            "cd ~; if [ -d irrigation ] && [ -f irrigation/docker-compose.yml ]; then cd irrigation; docker-compose down; fi; cd ~; if [ -d irrigation_local ] && [ -f irrigation_local/docker-compose.yml ]; then cd irrigation_local; docker-compose down; fi;",
                            "mkdir -p ~/irrigation_temp && cd ~/irrigation_temp && pwd",
                            "cd ~; if [ -d irrigation_local ]; then rm -r irrigation_local; fi; mv irrigation_temp/*/ irrigation_local; rm -r ~/irrigation_temp",
                            "cd ~/irrigation_local/; chmod +x gradlew && ./gradlew docker && docker-compose up -d"
                        )

                    assertThat(puts)
                        .singleElement()
                        .satisfies { map ->
                            assertThat(map)
                                .hasEntrySatisfying("from") { value ->
                                    assertThat(value).isEqualTo(project.rootDir)
                                }
                                .hasEntrySatisfying("into") { value ->
                                    assertThat(value as String).isEqualTo("/home/pi/somepath")
                                }
                        }
                }
        }

        def captureCommands(Session session) {
            SessionHandler sessionHandler = Mockito.mock(SessionHandler.class)

            Closure closure = session.getClosure()

            ArgumentCaptor<String> executeArgumentCaptor = ArgumentCaptor.forClass(String.class)
            ArgumentCaptor<HashMap> putArgumentCaptor = ArgumentCaptor.forClass(HashMap.class)

            Mockito.doAnswer { inv ->
                String command = inv.getArgument(0, String.class)
                if (command.contains("pwd") && command.contains("cd")) return "/home/pi/somepath"
                else return ""
            }
                .when(sessionHandler).execute(executeArgumentCaptor.capture() as String)
            Mockito.doNothing().when(sessionHandler).put(putArgumentCaptor.capture() as HashMap)

            Utility.callWithDelegate(closure, sessionHandler)

            return [
                executeArguments: executeArgumentCaptor.getAllValues(),
                putArguments    : putArgumentCaptor.getAllValues()
            ]
        }

        def assertSessionsCommandsList(List<Session> sessions) {
            assertHasOneSession(sessions)
                .extracting { session -> captureCommands(session) }
        }
    }

    private RunHandler fillRunHandler(Closure closure) {
        def runHandler = new RunHandler()
        Utility.callWithDelegate(closure, runHandler)
        return runHandler
    }

    void registerProperties(Map properties, String environment = "dev") {
        if (properties.containsKey("host")) {
            project.ext.setProperty("server.${environment}.host", properties.get("host"))
        }

        if (properties.containsKey("user")) {
            project.ext.setProperty("server.${environment}.user", properties.get("user"))
        }

        if (properties.containsKey("identity_file")) {
            project.ext.setProperty("server.${environment}.identity_file", properties.get("identity_file"))
        }
    }

    private Closure runAndCaptureClosure(Closure closure) {
        ArgumentCaptor<Closure> argumentCaptor = ArgumentCaptor.forClass(Closure.class)
        Mockito.when(ssh.run(argumentCaptor.capture())).thenReturn(null)

        closure()

        argumentCaptor.getValue()
    }

    private ObjectAssert<Session> assertHasOneSession(List<Session> sessions) {
        assertThat(sessions)
            .singleElement()
    }
}
