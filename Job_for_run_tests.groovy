def prepareTestData() {
    sh(mongoPath + '/mongorestore --username $MONGODB_USR --password $MONGODB_PSW --authenticationDatabase db ' +
            '--host=HOST:27017 --db=db dump/db/ --objcheck --drop')
}

properties([[$class: 'RebuildSettings', autoRebuild: false, rebuildDisabled: false],
            parameters([
                    choice(choices: ['None', '1', '2'], defaultValue: 'None', description: 'Test Suite for testing UI', name: 'TestSuiteUI'),
                    choice(choices: ['None', '1', '2'], defaultValue: 'None', description: 'Test Suite for testing API', name: 'TestSuiteAPI'),
                    choice(choices: ['None', '1', '2'], defaultValue: 'None', description: 'Test Suite for testing Database', name: 'TestSuiteDB'),
                    text(defaultValue: '', description: 'Set Test Run Id from TestRail', name: 'TestRailRunId'),
                    booleanParam(defaultValue: true, description: '', name: 'PrepareTestData')
            ])
])

pipeline {

    agent {
        label 'slave&&jdk17'
    }

    environment {
        mongoPath = tool name: 'MongoTools-100.5.1', type: 'com.cloudbees.jenkins.plugins.customtools.CustomTool'
        MONGODB = credentials('mongo-mbt-qa')
    }

    stages {
        stage('Write credentials') {
            steps {
                script {
                    withCredentials([file(credentialsId: 'id', variable: 'CONF')]) {
                        def conf = readFile encoding: 'UTF8', file: "$CONF"
                        writeFile encoding: 'UTF-8', file: './src/main/resources/config.properties', text: conf
                    }
                }
            }
        }

        stage('Clear Test Artifacts') {
            steps {
                script {
                    sh "mvn clean"
                }
            }
        }

        stage('Prepare Test Data') {
            when { expression { params.PrepareTestData } }
            steps {
                script {
                    prepareTestData()
                }
            }
        }

        stage('Running Database Tests') {
            steps {
                script {
                    if (params.TestSuiteDB != "None") {
                        sh "mvn test -Dremote=true -Dgroups=$TestSuiteDB -DrunId=$TestRailRunId"
                    }
                }
            }
        }

        stage('Running API Tests') {
            steps {
                script {
                    if (params.TestSuiteAPI != "None") {
                        sh "mvn test -Dgroups=$TestSuiteAPI-API -DrunId=$TestRailRunId"
                    }
                }
            }
        }

        stage('Running UI Tests') {
            steps {
                script {
                    if (params.TestSuiteUI != "None") {
                        echo "Running $TestSuiteUI Use Cases"
                        sh "mvn test -Dremote=true -Dgroups=$TestSuiteUI-UseCases -DrunId=$TestRailRunId"
                    }
                }
            }
        }
    }

    post {
       failure {
           emailext to: 'admin@test.com',
                   subject: "Regression Test's №${BUILD_NUMBER} build",
                   body: """
                       <br><b>Test's Result:</b><br>
                       <br>Build number: <b>${BUILD_NUMBER}</b>
                       <br>Build status: <b>${currentBuild.result}</b>
                       <br><b><a href=\"https://testrail.local/index.php?/runs/view/${TestRailRunId}&group_by=cases:section_id&group_order=asc\">TestRail Run</a></b>
                       <br><b><a href=\"https://jenkins.local/job/test-job/${BUILD_NUMBER}/allure/\">Allure Report</a></b>
                       """,
                   mimeType: 'text/html'
       }
       always {
           allure includeProperties: false, jdk: 'jdk17', results: [[path: 'target/allure-results/']]
       }
    }
}
