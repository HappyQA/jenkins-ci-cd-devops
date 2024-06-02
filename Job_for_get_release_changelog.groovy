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
                        return versions.data.findAll{it.released == false}.name.sort(versionStringSort).reverse()
                    ''']]],
                    
                    booleanParam(defaultValue: false, description: 'Module 1', name: 'MODULE 1'),
                    booleanParam(defaultValue: false, description: 'Module 2', name: 'MODULE 2'),
                    booleanParam(defaultValue: false, description: 'Module 3', name: 'MODULE 3'),
                    booleanParam(defaultValue: false, description: 'Module 4', name: 'MODULE 4'),
                    text(defaultValue: '', description: 'release ID from Jira', name: 'ID')     
        ])
])

pipeline {
    environment {
        TAG = "$VERSION"
        RELEASE_BRANCH = git.getReleaseBranchName("$VERSION")
        PROJECT_NAME = "test_project"
        PROJECT_KEY = "TEST"
        CURRENT_MODULE_NAME = ""
    }

    agent any

    stages {
        stage ('Get issues from version') {
            steps { 
                script {
                    if (params.MODULE 1) {
                        CURRENT_MODULE_NAME = ""
                    }
                    if (params.MODULE 2) {
                        CURRENT_MODULE_NAME = ""
                    }
                    if (params.MODULE 3) {
                        CURRENT_MODULE_NAME = ""
                    }
                    if (params.MODULE 4) {
                        CURRENT_MODULE_NAME = ""
                    }
                }
                echo "$CURRENT_MODULE_NAME"
            } 
        }
    }
    
    post {
        always {
            script {
                def issuesHtmlList = getIssuesHtmlList("$PROJECT_KEY", "$VERSION")
                emailext to: '',
                subject: "[TESTING] ${CURRENT_MODULE_NAME} ${VERSION}",
                body: """<img src=""><br><br>
                        <b>Bugs:</b><br><br>
                        <b>Project ${CURRENT_MODULE_NAME}</b><br>
                        $issuesHtmlList<br><br>
                        <b><a href=\"http://jira/projects/test_project/versions/${ID}/\">Fix Version of Project ${CURRENT_MODULE_NAME} ${VERSION} in Jira</a><b><br><br>""",
                mimeType: 'text/html' 
            }
        }
    }
}

def getIssuesHtmlList(String projectKey, String version) {
    def issues = jiraJqlSearch(jql: "project = '$projectKey' AND fixVersion = $version",
                               site: 'JIRA_SERVER').data.issues
    def resultHtml = ""
    for (issue in issues) {
            resultHtml = resultHtml.concat("<a href=\"http://jira/browse/${issue.key}\">$issue.key</a>: $issue.fields.summary<br>")
    }
    return resultHtml
}
