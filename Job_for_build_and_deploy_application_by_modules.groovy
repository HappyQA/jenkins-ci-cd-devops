#!groovy
@Library('') _

properties([[$class: 'RebuildSettings', autoRebuild: false, rebuildDisabled: false],
    parameters([
        [$class: 'CascadeChoiceParameter', choiceType: 'PT_SINGLE_SELECT',
        name: 'VERSION',
        description: 'Release version',
        referencedParameters: '',
        filterLength: 1, filterable: false, randomName: 'choice-parameter-415678720404050',
        script: [$class: 'GroovyScript', fallbackScript: [classpath: [], sandbox: false, script: ''],
            script: [classpath: [], sandbox: false,
                script: '''
                    import org.thoughtslive.jenkins.plugins.jira.service.JiraService;
                    import org.thoughtslive.jenkins.plugins.jira.Site;
                    import hudson.model.*

                    final Site config = new Site("JIRA_SERVER", new URL("http://jira/"), "BASIC", 10000);
                    config.setUserName('')
                    config.setPassword('');
                    def service = new JiraService(config);
                    def versions = service.getProjectVersions('test_project')

                    //sorting versions from jira to jenkins parameters

                    def versionArraySort = { a1, a2 ->
                        def headCompare = a1[0] <=> a2[0]
                        if (a1.size() == 1 || a2.size() == 1 || headCompare != 0) {
                            return headCompare
                        } else {
                            return recurse(a1[1..-1], a2[1..-1])
                        }
                    }
                    recurse = versionArraySort
                    def versionStringSort = { s1, s2 ->
                        def nums = { it.tokenize('.').collect{ it.toInteger() } }
                        versionArraySort(nums(s1), nums(s2))
                    }
                    return versions.data.findAll{it.released == false}.name.sort(versionStringSort)
                ''']]],

        booleanParam(defaultValue: false, description: '', name: 'USE_TAG'),
        booleanParam(defaultValue: true, description: 'Send to distrib/?', name: 'DISTRIB'),
        booleanParam(defaultValue: false, description: 'Cleaning workspace folder', name: 'CLEAN_WS'),
        booleanParam(defaultValue: false, description: 'Cleaning package-lock.json and nodejs folders', name: 'CLEAN_FE'),
        booleanParam(defaultValue: false, description: 'Do mvn clean', name: 'CLEAN_BE'),
        booleanParam(defaultValue: false, description: 'Build Module 1', name: 'MODULE 1'),
        booleanParam(defaultValue: false, description: 'Build Module 2', name: 'MODULE 2'),
        booleanParam(defaultValue: false, description: 'Build Module 3', name: 'MODULE 3'),
        booleanParam(defaultValue: false, description: 'Build Module 4', name: 'MODULE 4'),
        booleanParam(defaultValue: false, description: 'Build Module 5', name: 'MODULE 5'),
        booleanParam(defaultValue: true, description: 'Deploy applications?', name: 'DEPLOY'),
        text(defaultValue: '', description: 'Special notes to support / tester', name: 'SPECIAL_NOTES')
    ])
])

pipeline {

    environment {
        TAG = "$VERSION"
        TARGET = "/target/distrib/"
        RELEASE_BRANCH = git.getReleaseBranchName("$VERSION")
        PROJECT_NAME = "test_project"
        PROJECT_KEY = "TEST"
        CURRENT_MODULE_NAME = ""
        CURRENT_STAGE_NAME = ""
    }
    agent any

    tools {
        maven 'Maven3.6.0'
        nodejs 'NodeJS 10'
        jdk 'java11'
        nodejs 'nodejs-jest'
    }
    
    options {
        buildDiscarder(logRotator(numToKeepStr: '7'))
        disableConcurrentBuilds()
    }

    stages {
        stage ('Clean workspace') {
            when {expression {params.CLEAN_WS}}
            steps {
                script {
                    CURRENT_STAGE_NAME = "${env.STAGE_NAME}"
                    cleanWs()
                }
            }
        }

        stage ('VCS') {
            steps {
                script {
                    CURRENT_STAGE_NAME = "${env.STAGE_NAME}"
                    sh "git fetch --prune"
                    if (params.USE_TAG) {
                        echo "Checkout to the git tag '$TAG'"
                        sh "git checkout $TAG"
                    } else {
                        if(git.isFixBuild("$VERSION")) {
                            echo "Checkout to release branch '$RELEASE_BRANCH'"
                            sh "git checkout $RELEASE_BRANCH"
                            sh "git pull"
                            sh "git pull --tags"
                        } else {
                            echo "Creating branch '$RELEASE_BRANCH'"
                            sh "git branch -d $RELEASE_BRANCH | true"
                            sh "git checkout -b $RELEASE_BRANCH"
                            sh "git push --set-upstream origin $RELEASE_BRANCH"
                        }
                        echo "create tag '$VERSION'"
                        sh "git tag -f $TAG -m 'tag has been set a jenkins'"
                        sh "git push --tags -f origin"
                    }
                }
            }
        }

        stage ('Clean Frontend') {
            when {expression {params.CLEAN_FE}}
            steps {
                script {
                    CURRENT_STAGE_NAME = "${env.STAGE_NAME}"
                    jenkins.cleanApplication()
                }
            }
        }

        stage ('Clean Backend') {
            when {expression {params.CLEAN_BE}}
            steps {
                script {
                    CURRENT_STAGE_NAME = "${env.STAGE_NAME}"
                    sh "mvn clean"
                }
            }
        }

        stage ('Prepare Frontend') {
            steps {
                script {
                    CURRENT_STAGE_NAME = "${env.STAGE_NAME}"
                    dir("angular/") {
                        sh "npm i"
                    }
                }
            }
        }

        stage ('Build Frontend modules') {
            steps {
                echo 'Choosed modules from Frontend are building...'
                script {
                    CURRENT_STAGE_NAME = "${env.STAGE_NAME}"
                    CURRENT_MODULE_NAME = "Module 0"
                    jenkins.buildFronetndApplication("")
                    CURRENT_MODULE_NAME = "Module 0.0"
                    jenkins.buildFronetndApplication("")

                    if (params.MODULE 1) {
                        CURRENT_MODULE_NAME = ""
                        jenkins.buildFronetndApplication("")
                    }
                    if (params.MODULE 2) {
                        CURRENT_MODULE_NAME = ""
                        jenkins.buildFronetndApplication("")
                    }
                    if (params.MODULE 3) {
                        CURRENT_MODULE_NAME = ""
                        jenkins.buildFronetndApplication("")
                    
                    if (params.MODULE 4) {
                        CURRENT_MODULE_NAME = ""
                        jenkins.buildFronetndApplication("")
                    }
                    if (params.MODULE 5) {
                        CURRENT_MODULE_NAME = ""
                        jenkins.buildFronetndApplication("")
                    }
                }
            }
        }

        stage ('Build Backend modules') {
            steps {
                echo 'Choosed modules from Backend are building...'
                script {
                    CURRENT_STAGE_NAME = "${env.STAGE_NAME}"
                    CURRENT_MODULE_NAME = "module 0"
                    jenkins.buildBackendApplication("")
                    jenkins.writeMD5SUMMfiles("")

                    CURRENT_MODULE_NAME = "module 0.0"
                    jenkins.buildBackendApplication("")
                    jenkins.writeMD5SUMMfiles("")

                    if (params.MODULE 1) {
                        CURRENT_MODULE_NAME = ""
                        jenkins.buildBackendApplication("")
                        jenkins.getValidationSchemas("")
                        jenkins.getExportMapping("")
                        jenkins.getTemplates("")
                        jenkins.writeMD5SUMMfiles("")
                    }
                    if (params.MODULE 2) {
                        CURRENT_MODULE_NAME = ""
                        jenkins.buildBackendApplication("")
                        jenkins.writeMD5SUMMfiles("")
                    }
                    if (params.MODULE 3) {
                        CURRENT_MODULE_NAME = ""
                        jenkins.buildBackendApplication("")
                        jenkins.writeMD5SUMMfiles("")
                    }
                    if (params.MODULE 4) {
                        CURRENT_MODULE_NAME = ""
                        jenkins.buildBackendApplication("")
                        jenkins.writeMD5SUMMfiles("")
                    }
                    if (params.MODULE 5) {
                        CURRENT_MODULE_NAME = ""
                        jenkins.buildBackendApplication("")
                        jenkins.writeMD5SUMMfiles("")
                    }
                }
            }
            post {
                always {
                    junit '*/target/surefire-reports/*xml'
                }
            }
        }

        stage ('Publish distrib to www') {
            when {expression {params.DISTRIB}}
            steps {
                script {
                    CURRENT_STAGE_NAME = "${env.STAGE_NAME}"
                    parallel (
                        module 0: {
                            jenkins.publishApplication("")
                        },
                        module 0.0: {
                            jenkins.publishApplication("")
                        },
                        module 1: {
                        if (params.MODULE 1) {
                                jenkins.publishApplication("")
                            }
                        },
                        module 2: {
                            if (params.MODULE 2) {
                                jenkins.publishApplication("")
                            }
                        },
                        module 3: {
                            if (params.MODULE 3) {
                                jenkins.publishApplication("")
                            }
                        },
                        module 4: {
                            if (params.MODULE 4) {
                                jenkins.publishApplication("")
                            }
                        },
                        module 5: {
                            if (params.MODULE 5) {
                                jenkins.publishApplication("")
                            }
                        }
                    )
                }
            }
        }

        stage ('Deploy on instance') {
            when {expression {params.DEPLOY}}
            steps {
                script {
                    CURRENT_STAGE_NAME = "${env.STAGE_NAME}"
                    echo 'Delivery distrib on optional Environment'
                    if (params.MODULE 1) {
                        echo 'Deploy on host 1/module 1'
                        jenkins.deployAllApplications("project", "module 1")
                    } else {
                        if (params.MODULE 2) {
                            echo 'Deploy on host 2/module 2'
                            jenkins.deployAllApplications("project", "module 2")
                        } else {
                            echo 'Deploy on host 2/module 3'
                            jenkins.deployAllApplications("project", "module 3")
                        }
                    }
                }
            }
        }
    }

    post {
        success {
            script {
                def issuesHtmlList = jira.getIssuesHtmlList("$PROJECT_KEY", "$VERSION")
                def versionDescription = jira.getVersionDescription("$PROJECT_KEY", "$VERSION")
                emailext to: '',
                    subject: "[TESTING] ${CURRENT_MODULE_NAME} ${VERSION}",
                    body: """<img src=""><br><br>
                            <b>Dist</b><br>
                            <a href=\"http://distrib/project/distrib/$VERSION/\">http://distrib/project/distrib/$VERSION/</a><br><br>
                            <b>Description</b><br>
                            $versionDescription<br><br>
                            <b>Changelog</b><br>
                            $issuesHtmlList<br><br>
                            $SPECIAL_NOTES<br><br>""",
                    mimeType: 'text/html',
                    replyTo: ''

                jira.releaseVersion("$PROJECT_KEY", "$VERSION")
            }
        }
        failure {
            emailext to: '',
            subject: "[TESTING] ${CURRENT_MODULE_NAME} ${VERSION}. Build ${currentBuild.fullDisplayName} failed",
            body: "Build ${currentBuild.fullDisplayName} FAILED. <br> ${BUILD_URL} - look on logs",
            mimeType: 'text/html',
            replyTo: ''
        }
        always {
            script {
                slackNotifier.sendRC(currentBuild.currentResult, "project", "key")
            }
        }
    }
}
