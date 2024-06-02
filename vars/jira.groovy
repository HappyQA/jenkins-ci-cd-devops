#!groovy

def getIssuesHtmlList(String projectKey, String version) {
    def issues = jiraJqlSearch(jql: "project = '$projectKey' AND fixVersion = $version",
                               site: 'JIRA_SERVER').data.issues
    def resultHtml = ""
    for (issue in issues) {
            resultHtml = resultHtml.concat("<a href=\"http://jira/browse/${issue.key}\">$issue.key</a>: $issue.fields.summary<br>")
    }
    return resultHtml
}

def releaseVersion(String projectKey, String version) {
    def updatedVersion = jiraGetProjectVersions(idOrKey: "$projectKey", site: 'JIRA_SERVER').data.find{it.name == "$version"}
    jiraEditVersion(id: updatedVersion.id,
                    version: [ released:true, releaseDate: new Date().format("yyyy-MM-dd")],
                    site: 'JIRA_SERVER')
}

def getVersionDescription(String projectKey, String version) {
    def getVersion = jiraGetProjectVersions(idOrKey: "$projectKey", site: 'JIRA_SERVER').data.find{it.name == "$version"}.description
    return getVersion
}

def setFixVersion(issues, String projectKey, String version) {
    def jiraFixVersion = jiraGetProjectVersions(idOrKey: "$projectKey", site: 'JIRA_SERVER').data.find{it.name == "$version"}
    if(issues.length != 0)
        issues.each{ issue -> jiraEditIssue(idOrKey: issue.key, issue: [ fields: [fixVersions: [jiraFixVersion]] ], site: 'JIRA_SERVER') }
    
}

def getNoMergedIssuesByProjectVersion(String projectKey, String version) {
    def issuesInVersion = jiraJqlSearch(jql: "project = '$projectKey' AND fixVersion = $version", 
                                        site: 'JIRA_SERVER').data.issues
    return issuesInVersion.findAll { isIssueNoMerged(it.key) }
}

def getMergedIssuesInUnreleasedVersions(String projectKey, String version) {
    def issuesInVersion = jiraJqlSearch(jql: "project = '$projectKey' AND ((fixVersion in unreleasedVersions() AND fixVersion != $version) OR fixVersion is EMPTY ) AND status = 'In Testing'", 
                                        site: 'JIRA_SERVER').data.issues
    return issuesInVersion.findAll { isIssueMerged(it.key) }
}

def isIssueNoMerged(String issueKey) {
    return !sh(script: "git branch -lr --no-merged | grep $issueKey | cat",
               returnStdout: true).trim().isEmpty()
}

def isIssueMerged(String issueKey) {
    return !sh(script: "git branch -lr --merged | grep $issueKey | cat", returnStdout: true).trim().isEmpty()
}

def getIssuesSlackList(String projectKey, String version) {
    def issues = jiraJqlSearch(jql: "project = $projectKey AND fixVersion = $version",
                               site: 'JIRA_SERVER').data.issues
    def resultSlack = ""
    for (issue in issues) { 
            resultSlack = resultSlack.concat("http://jira/browse/${issue.key}: $issue.fields.summary \n")
    }
    return resultSlack
}

 def setNewVersion = jira.CreateNewVersion("$VERSION","$DESCRIPTION")

 def CreateNewVersion (String version, String description) {
     def newVersion = [ name: "$version",
                     archived: false,
                     released: false,
                     description: "$description",
                     project: 'test_project' ]
     jiraNewVersion version: newVersion, site: 'JIRA_SITE'
 }
