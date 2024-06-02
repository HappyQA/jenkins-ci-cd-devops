#!groovy
@Library('') _

properties([[$class: 'RebuildSettings', autoRebuild: false, rebuildDisabled: false],
    parameters([
            booleanParam(defaultValue: false, description: 'Cleaning workspace folder', name: 'CLEAN_WS'),
            booleanParam(defaultValue: false, description: 'Cleaning package-lock.json and nodejs folders', name: 'CLEAN_FE'),
            booleanParam(defaultValue: true, description: 'Deploy on DEV environment?', name: 'DEV'),
            booleanParam(defaultValue: true, description: 'Build all modules?', name: 'ALL')
        ])
])

pipeline {

    environment {

        CURRENT_STAGE_NAME = ""
        PROJECT_NAME = "test_project"
    }

    agent any

    tools {
        maven 'Maven3.6.0'
        nodejs 'NodeJS 10'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
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

        stage ('Clean Frontend') {
            when {expression {params.CLEAN_FE}}
            steps {
                cleanApplication()
            }
        }

        stage ('Frontend - Run Test') {
            steps {
                script {
                    CURRENT_STAGE_NAME = "${env.STAGE_NAME}"
                    dir('angular/') {
                        sh "npm i"
                        sh "npx nx run-many --target=test --all --parallel=true --maxParallel=3"
                    }    
                }                      
            }  
        }

        stage ('Frontend - Run Build') {
            steps {
                script {
                    CURRENT_STAGE_NAME = "${env.STAGE_NAME}"
                    dir('angular/') {
                        sh "npx nx run-many --target=build --all --parallel=true --maxParallel=3 --prod"
                        sh "npm run build-storybook"
                    }    
                }                      
            }  
        }

        stage ('Clean Backend (Do mvn clean)') {
            steps {
                script {
                    CURRENT_STAGE_NAME = "${env.STAGE_NAME}"
                    sh "mvn clean"
                }
            } 
        }
        
        stage ('Backend - Compile Stage') {
            when {expression {params.ALL}}
            steps {
                script {
                    CURRENT_STAGE_NAME = "${env.STAGE_NAME}"
                    sh "mvn compile -DSkipTests"
                }
            }
        }

        stage ('Backend - Package Stage') {
            when {expression {params.ALL}}
            steps {
                script {
                    CURRENT_STAGE_NAME = "${env.STAGE_NAME}"
                    sh "mvn package -P production"
                } 
            }
        }

        stage ('Package Additional Stuff') {
            steps {
                script {
                    CURRENT_STAGE_NAME = "${env.STAGE_NAME}"
                    packageAllAPI()
                    junit '*/target/surefire-reports/*xml'
                    getValidationSchemes("")
                    getExportMapping("")
                    getTemplates("")
                }
            }
        }

        stage('Deploy on DEV') {
            when {expression {params.DEV && params.ALL}}
            steps {
                script {
                    CURRENT_STAGE_NAME = "${env.STAGE_NAME}"
                    echo 'Delivery distrib to staging dev environment'
                    deployAllApplicationsDEV("project_dev")
                }
            }
        }
    }

    post {
        failure {
            script {
                sendEmailToDev()
                slackNotifier.send(currentBuild.currentResult, "test_project", "key") 
            }
        }    
    }
}

def deployAllApplicationsDEV(String path_to) {
    echo "Deploy all applications on DEV Env"
    deployApplicationDEV("module 0", "$path_to")
    deployApplicationDEV("module 0.0", "$path_to")
    deployApplicationDEV("module 1", "$path_to")
    deployApplicationDEV("module 2", "$path_to")
    deployApplicationDEV("module 3", "$path_to")
    deployApplicationDEV("module 4", "$path_to")
    deployApplicationDEV("module 5", "$path_to")

}

def deployApplicationDEV(String app, String path_to) {
    echo "Deploy $app"
    sshPublisher(publishers: [sshPublisherDesc(
                            configName: 'test config number',
                            transfers: [
                                // remove previous build
                                sshTransfer(
                                    excludes: '',
                                    execCommand: "rm -r --force ~/$path_to/$app/{lib,frontend}",
                                    execTimeout: 120000,
                                    flatten: false,
                                    makeEmptyDirs: false,
                                    noDefaultExcludes: false,
                                    patternSeparator: '[, ]+',
                                    remoteDirectory: '',
                                    remoteDirectorySDF: false,
                                    removePrefix: '',
                                    sourceFiles: ''
                                ),
                                // app distrib
                                sshTransfer(
                                    excludes: "test_project_$app/target/distrib/resources/,project_$app/target/distrib/${app}.sh",
                                    execCommand: "chmod +x ~/$path_to/$app/${app}.sh && ~/$path_to/$app/${app}.sh -a restart",
                                    execTimeout: 120000,
                                    flatten: false,
                                    makeEmptyDirs: false,
                                    noDefaultExcludes: false,
                                    patternSeparator: '[, ]+',
                                    remoteDirectory: "$path_to/$app",
                                    remoteDirectorySDF: false,
                                    removePrefix: "test_project_$app/target/distrib/",
                                    sourceFiles: "test_project_$app/target/distrib/",
                                    usePty: true
                                )],
                                usePromotionTimestamp: false,
                                useWorkspaceInPromotion: false,
                                verbose: false)])
}

def getValidationSchemes (String app) {
    def validationSchemesDir = "test_project_${app}/target/distrib/validation_schemes"
    sh "mkdir $validationSchemesDir"
    sh "cp -r --force test_project/common/${app}/validation_schemes/* $validationSchemesDir/"
}
def getExportMapping (String app) {
    def exportMappingsDir = "test_project_${app}/target/distrib/export_mapping"
    sh "mkdir $exportMappingsDir"
    sh "cp -r --force test_project/${app}/export_mapping/* $exportMappingsDir/"
}

def getTemplates (String app) {
    sh "cp -r --force test_project_${app}/templates/* test_project_${app}/target/distrib/templates/"
}

def packageAllAPI() {
    def pathToAPI = "target/generated-docs"
    def pathToFrontend = "target/distrib/frontend"

    def modules = [
        "module 1",
        "module 2",
        "module 3",
        "module 4",
    ]
    modules.each { module -> sh "cp ${module}/${pathToAPI}/* ${module}/${pathToFrontend}/" }
}

def packageApplicationAPI(String app) {
    def pathToAPI = "target/generated-docs"
    def pathToFrontend = "target/distrib/frontend"

    sh "cp test_project_${app}/${pathToAPI}/* test_project_${app}/${pathToFrontend}/"
}

def sendEmailToDev () {
    def changeLog = changeLogs()
        emailext to: '',
        attachLog: true,
        compressLog: false,
        subject: "[DEV] Stage ${CURRENT_STAGE_NAME} are failed - Build ${BUILD_NUMBER}",
        body: """
            <img src=""><br><br>
            <br><b>Results:</b><br>
            <br>Number: <b>${BUILD_NUMBER}</b>
            <br>Status: <b>${currentBuild.result}</b>
            <br>Latest commits from SCM:
            $changeLog
            <br><b><a href="">Look on report</a></b>
            """,
        mimeType: 'text/html',
        replyTo: ''
}

def prevBuildLastCommitId() {
    def prev = currentBuild.previousBuild
    def items = null
    def result = null
    if (prev != null && prev.changeSets != null && prev.changeSets.size() && prev.changeSets[prev.changeSets.size() - 1].items.length) {
        items = prev.changeSets[prev.changeSets.size() - 1].items
        result = items[items.length - 1].commitId
    }
    return result
}

def commitInfo(commit) {
    return commit != null ? "`${commit.commitId.take(7)}`  *${commit.msg}*  от <b>${commit.author}</b>\n" : ""
}

def changeLogs() {
    String msg = ""
    String repoUrl = 'bitbucket url'
    def lastId = null
    def prevId = prevBuildLastCommitId()
    def changeLogSets = currentBuild.changeSets

    for (int i = 0; i < changeLogSets.size(); i++) {
        def entries = changeLogSets[i].items
        for (int j = 0; j < entries.length; j++) {
            def entry = entries[j]
            lastId = entry.commitId
            msg = msg + "<br>${commitInfo(entry)}"
        }
    }
    if (prevId != null && lastId != null) {
        msg = msg + "<br>\n<b>Latest commit in build:</b> ${repoUrl}/commits/${lastId}\n"
    }
    return msg
}

def cleanApplication () {
    echo "Clean node_modules and package-lock.json in angular folder's"
    def dirs = [
        "test_project_module 0/angular",
        "test_project_module 1/angular",
        "test_project_module 2/angular",
        "test_project_module 3/angular",
        "test_project_module 4/angular",
        "test_project_module 5/angular",
        "test_project_module 6/angular",
        "test_project_module 7/angular",
        "angular/"
    ]

    dirs.each{ dir -> sh "rm -rf test_project/$dir/{node_modules,package-lock.json}"}
    sh(script: "npm cache clean --force")
}
