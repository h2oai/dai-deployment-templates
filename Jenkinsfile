#!/usr/bin/groovy

@Library('test-shared-library@1.19') _

import ai.h2o.ci.Utils

//JAVA_IMAGE = 'harbor.h2o.ai/dockerhub-proxy/library/openjdk:8u222-jdk-slim'
JAVA_IMAGE = 'adoptopenjdk/openjdk8:x86_64-alpine-jdk8u292-b10-slim'
NODE_LABEL = 'docker'
DOCKERHUB_CREDS = 'dockerhub'
HARBOR_URL = "http://harbor.h2o.ai/"
HARBOR_CREDS = 'harbor.h2o.ai'
VORVAN_CRED = 'MM_GCR_VORVAN_CREDENTIALS'

def versionText = null
def utilsLib = new Utils()

pipeline {
    // Specify agent on a per stage basis.
    agent none

    // Setup job options.
    options {
        ansiColor('xterm')
        timestamps()
        timeout(time: 180, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    parameters {
        booleanParam(
            name: 'PUSH_TO_HARBOR',
            defaultValue: false,
            description: 'Whether to also push Docker images to Harbor.',
        )
        booleanParam(
            name: 'PUSH_TO_DOCKERHUB',
            defaultValue: false,
            description: 'Whether to also push Docker images to DockerHub.',
        )
        booleanParam(
            name: 'PUSH_DISTRIBUTION_ZIP',
            defaultValue: false,
            description: 'Whether to also push distribution ZIP archive to S3.',
        )
        booleanParam(
                name: 'PUSH_TO_VORVAN',
                defaultValue: false,
                description: 'Whether to also push Docker images to h2o.ai maintained gcr.io repo Vorvan.',
        )
    }

    stages {

        stage('1. Init') {
            agent { label NODE_LABEL }
            steps {
                script {
                    deleteDir()
                    checkout scm
                }
            }
        }

        stage('2. Test') {
            // Run inside JAVA_IMAGE container on NODE_LABEL host.
            agent {
                docker {
                    //registryCredentialsId HARBOR_CREDS
                    //registryUrl HARBOR_URL
                    image JAVA_IMAGE
                    label NODE_LABEL
                }
            }
            steps {
                timeout(time: 30, unit: 'MINUTES') {
                    script {
                        versionText = getVersion()
                        echo "Version: ${versionText}"
                        sh "./gradlew check"
                    }
                }
            }
            post {
                always {
                    testReport 'common/transform/build/reports/tests/test', 'JUnit tests: common/transform'
                }
            }
        }

        stage('3. Build') {
            // Run inside JAVA_IMAGE container on NODE_LABEL host.
            agent {
                docker {
                    //registryCredentialsId HARBOR_CREDS
                    //registryUrl HARBOR_URL
                    image JAVA_IMAGE
                    label NODE_LABEL
                }
            }
            steps {
                timeout(time: 60, unit: 'MINUTES') {
                    script {
                        sh "./gradlew distributionZip"
                        if (isReleaseVersion(versionText)) {
                            utilsLib.appendBuildDescription("Release ${versionText}")
                        }
                    }
                }
            }
            post {
                success {
                    arch "build/dai-deployment-templates-${versionText}.zip"
                    stash name: "distribution-zip", includes: "build/dai-deployment-templates-${versionText}.zip"
                }
            }
        }

        stage('4. Publish to S3') {
            // Run on NODE_LABEL host.
            agent { label NODE_LABEL }
            when {
                expression {
                    return isReleaseBranch() || isMasterBranch() || params.PUSH_DISTRIBUTION_ZIP
                }
            }
            steps {
                timeout(time: 30, unit: 'MINUTES') {
                    script {
                        unstash name: "distribution-zip"
                        s3upDocker {
                            localArtifact = "build/dai-deployment-templates-${versionText}.zip"
                            artifactId = 'dai-deployment-templates'
                            version = "${versionText}"
                            keepPrivate = false
                            isRelease = isReleaseVersion(versionText)
                            platform = "any"
                        }
                    }
                }
            }
            post {
                success {
                    script {
                        echo "Keep this build.."
                        currentBuild.setKeepLog(true)
                    }
                }
            }
        }

        stage('5. Push Docker Images To Harbor') {
            when {
                expression {
                    return isReleaseBranch() || isMasterBranch() || params.PUSH_TO_HARBOR
                }
            }
            agent {
                docker {
                    //registryCredentialsId HARBOR_CREDS
                    //registryUrl HARBOR_URL
                    image JAVA_IMAGE
                    label NODE_LABEL
                }
            }
            steps {
                timeout(time: 30, unit: 'MINUTES') {
                    script {
                        def gitCommitHash = env.GIT_COMMIT
                        def imageTags = isMasterBranch() || isReleaseBranch() ? "${versionText},${gitCommitHash}" : "${gitCommitHash}"
                        withDockerCredentials(DOCKERHUB_CREDS, "FROM_") {
                            withDockerCredentials("harbor.h2o.ai", "TO_") {
                                sh "./gradlew jib \
                                -Djib.to.auth.username=${TO_DOCKER_USERNAME} \
                                -Djib.to.auth.password=${TO_DOCKER_PASSWORD} \
                                -Djib.from.auth.username=${FROM_DOCKER_USERNAME} \
                                -Djib.from.auth.password=${FROM_DOCKER_PASSWORD} \
                                -Djib.to.tags=${imageTags} \
                                -Djib.allowInsecureRegistries=true \
                                -DsendCredentialsOverHttp=true"
                            }
                        }
                    }
                }
            }
        }

        stage('6. Push Docker Images To DockerHub') {
            when {
                expression {
                    return isReleaseBranch() || params.PUSH_TO_DOCKERHUB
                }
            }
            agent {
                docker {
                    //registryCredentialsId HARBOR_CREDS
                    //registryUrl HARBOR_URL
                    image JAVA_IMAGE
                    label NODE_LABEL
                }
            }
            steps {
                timeout(time: 30, unit: 'MINUTES') {
                    script {
                        def gitCommitHash = env.GIT_COMMIT
                        def imageTags = isMasterBranch() || isReleaseBranch() ? "${versionText},${gitCommitHash}" : "${gitCommitHash}"
                        withDockerCredentials(DOCKERHUB_CREDS, "FROM_") {
                            withDockerCredentials(DOCKERHUB_CREDS, "TO_") {
                                sh "./gradlew jib \
                                -Djib.to.auth.username=${TO_DOCKER_USERNAME} \
                                -Djib.to.auth.password=${TO_DOCKER_PASSWORD} \
                                -Djib.from.auth.username=${FROM_DOCKER_USERNAME} \
                                -Djib.from.auth.password=${FROM_DOCKER_PASSWORD} \
                                -Djib.to.tags=${imageTags} \
                                -PdockerRepositoryPrefix=h2oai/"
                            }
                        }
                    }
                }
            }
        }

        stage('7. Push Docker Images To GoogleCloud') {
            when {
                expression {
                    return isReleaseBranch() || params.PUSH_TO_VORVAN
                }
            }
            agent {
                docker {
                    //registryCredentialsId HARBOR_CREDS
                    //registryUrl HARBOR_URL
                    image JAVA_IMAGE
                    label NODE_LABEL
                }
            }
            steps {
                timeout(time: 30, unit: 'MINUTES') {
                    script {
                        def gitCommitHash = env.GIT_COMMIT
                        def imageTags = isMasterBranch() || isReleaseBranch() ? "${versionText},${gitCommitHash}" : "${gitCommitHash}"
                        withDockerCredentials(DOCKERHUB_CREDS, "FROM_") {
                            withGCRCredentials(VORVAN_CRED) {
                                def gcrCreds = readFile("${GCR_JSON_KEY}")
                                withEnv(['TO_DOCKER_USERNAME=_json_key', "TO_DOCKER_PASSWORD=${gcrCreds}"]) {
                                    sh "./gradlew jib \
                                    -Djib.from.auth.username=${FROM_DOCKER_USERNAME} \
                                    -Djib.from.auth.password=${FROM_DOCKER_PASSWORD} \
                                    -Djib.to.tags=${imageTags} \
                                    -PdockerRepositoryPrefix=gcr.io/vorvan/h2oai/"
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Returns version specified in gradle.properties.
 *
 * Fails if master contains a release version (to prevent pushing release version accidentally).
 */
def getVersion() {
    def version = sh(
            script: "./gradlew -q -Dorg.gradle.internal.launcher.welcomeMessageEnabled=false printVersion",
            returnStdout: true).trim()
    if (!version) {
        error "Version must be set"
    }
    if (isMasterBranch() && isReleaseVersion(version)) {
        error "Master contains a non-snapshot version"
    }
    return version
}

/**
 * Returns true, if the given version string denotes a release (not a snapshot) version.
 */
def isReleaseVersion(version) {
    return !version.endsWith("-SNAPSHOT")
}

/**
 * Returns true, if we are on the master branch.
 */
def isMasterBranch() {
    return env.BRANCH_NAME == "master"
}

/**
 * Returns true, if we are on a release branch.
 */
def isReleaseBranch() {
    return env.BRANCH_NAME.startsWith("release")
}

/** Context manager that runs content with set up credentials for a Docker repository. */
def withDockerCredentials(String credentialsId, Closure body) {
    withDockerCredentials(credentialsId, "", body)
}

def withDockerCredentials(String credentialsId, String prefix, Closure body) {
    def dockerCredentials = usernamePassword(
            credentialsId: credentialsId,
            passwordVariable: "${prefix}DOCKER_PASSWORD",
            usernameVariable: "${prefix}DOCKER_USERNAME"
    )
    withCredentials([dockerCredentials]) {
        body()
    }
}

void withGCRCredentials(final String credentialsId, final Closure body) {
    gcrCredentials = file(credentialsId: credentialsId, variable: 'GCR_JSON_KEY')
    withCredentials([gcrCredentials]) {
        body()
    }
}

