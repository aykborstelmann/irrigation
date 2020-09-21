package org.irrigation.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.hidetake.groovy.ssh.core.Remote
import org.hidetake.groovy.ssh.core.Service

import java.nio.file.Paths

class DeployTask extends DefaultTask {
    final static String REPOSITORY_URL = "https://github.com/aykborstelmann/irrigation.git"

    @Optional
    @Input
    @Option(option = "local", description = "Instead of cloning the GitHub repo copy the local version")
    Boolean local;

    @Optional
    @Input
    @Option(option = "env", description = "Configures the environment to be used")
    String environment = "dev";

    @Optional
    @Input
    @Option(option = "branch", description = "Configures the branch which should be deployed")
    String branch = "master";

    @TaskAction
    void deploy() {
        def host = getPropertyOrThrow("server.${environment}.host", "Missing host")
        def user = getPropertyOrThrow("server.${environment}.user", "Missing user")
        def identity = project.findProperty("server.${environment}.identity_file") as String ?: "${System.properties['user.home']}/.ssh/id_rsa"

        def identityFile = Paths.get(identity);


        def ssh = project.extensions.findByName("ssh") as Service

        println "Deploying to $user@$host with identity file ${identityFile.toString()}"

        if (local) {
            println "Copying ${project.getRootDir()} to remote host"
        } else {
            println "Cloning / fetching repository and checkout $branch"
        }

        ssh.run {
            session(host: host, user: user, identity: identityFile.toFile()) {
                println "Stop running docker-compose"
                execute "cd ~; if [ -d irrigation ] && [ -f irrigation/docker-compose.yml ]; then cd irrigation; docker-compose down; fi; cd ~; if [ -d irrigation_local ] && [ -f irrigation_local/docker-compose.yml ]; then cd irrigation_local; docker-compose down; fi;"
                if (!local) {
                    println "Clone repository if not existing"
                    execute "cd ~; if [ ! -d irrigation ]; then git clone $REPOSITORY_URL irrigation; fi;"

                    println "Pull and checkout $branch"
                    execute "cd ~/irrigation/; git pull; if [ -z \$(git branch --list $branch) ] ; then git checkout -b $branch origin/$branch; fi;"

                    println "Build docker images and run docker-compose"
                    execute "cd ~/irrigation/; docker-compose build && docker-compose up -d"
                } else {
                    execute "Copy local version to remote"

                    def path = execute "mkdir -p ~/irrigation_temp && cd ~/irrigation_temp && pwd"
                    put from: project.getRootDir(), into: path, filter: { File file ->
                        def filePath = file.toPath().toString()
                        def containsIllegalFile = filePath.contains("\\.git\\") || filePath.contains("\\.gradle\\")
                        !containsIllegalFile
                    }
                    execute "cd ~; if [ -d irrigation_local ]; then rm -r irrigation_local; fi; mv irrigation_temp/*/ irrigation_local; rm -r ~/irrigation_temp"

                    println "Build docker images and run docker-compose"
                    execute "cd ~/irrigation_local/; docker-compose build && docker-compose up -d"
                }
            }
        }

    }

    def getPropertyOrThrow(property, msg) {
        def user = project.findProperty(property)
        assert user != null: "$msg, please set \"$property\" property"
        user
    }
}