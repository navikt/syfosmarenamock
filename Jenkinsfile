#!/usr/bin/env groovy

pipeline {
    agent any

    tools {
        jdk 'openjdk11'
    }

    environment {
        APPLICATION_NAME = 'syfosmmqmock'
        DOCKER_SLUG = 'syfo'
    }

    stages {
        stage('initialize') {
            steps {
                init action: 'default'
                script {
                    sh(script: './gradlew clean')
                    def applicationVersionGradle = sh(script: './gradlew -q printVersion', returnStdout: true).trim()
                    env.APPLICATION_VERSION = "${applicationVersionGradle}-${env.COMMIT_HASH_SHORT}"
                    init action: 'updateStatus', applicationName: env.APPLICATION_NAME, applicationVersion: env.APPLICATION_VERSION
                }
            }
        }
        stage('build') {
            steps {
                sh './gradlew build -x test'
            }
        }
        stage('run tests (unit & intergration)') {
            steps {
                sh './gradlew test'
                slackStatus status: 'passed'
            }
        }
        stage('create uber jar') {
            steps {
                sh './gradlew shadowJar'
            }
        }
        stage('build docker image') {
            steps {
                dockerUtils action: 'createPushImage'
            }
        }
        stage('deploy arena mq mock to production') {
            steps {
                deployApp action: 'kubectlDeploy', cluster: 'prod-fss', file: 'naiserator.yaml', placeholderFile: 'arena_reader_prod.env'
            }
        }
        stage('deploy syfoservice mq mock to production') {
            steps {
                deployApp action: 'kubectlDeploy', cluster: 'prod-fss', file: 'naiserator.yaml', placeholderFile: 'syfoservice_reader_prod.env'
            }
        }
        stage('deploy infotrygd mq mock to production') {
            steps {
                deployApp action: 'kubectlDeploy', cluster: 'prod-fss', file: 'naiserator.yaml', placeholderFile: 'infotrygd_reader_prod.env'
            }
        }
    }
    post {
        always {
            postProcess action: 'always'
        }
        success {
            postProcess action: 'success'
        }
        failure {
            postProcess action: 'failure'
        }
    }
}
