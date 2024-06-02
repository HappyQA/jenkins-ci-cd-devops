#!groovy

def getLastCommitInfo() {
    return sh(script:"git show --pretty='Commit hash: %H%nCommit date: %cd%nAuthor: %an%nCommitter: %cn' --no-notes --no-patch",
              returnStdout: true).trim()
}

def isFixBuild(String version) {
    return !version.tokenize('.').last().equals('0')
}

def getReleaseBranchName(String version){
    return "release/" + version.substring(0, version.lastIndexOf('.')) + ".0"
}

def GetCommitLogFromVersion () {
    return sh(script:"git log -g origin/release/0.55.0 --pretty='Commit hash: %H%nCommit date: %cd%nAuthor: %an%nCommitter: %cn' --no-notes --no-patch",
                returnStdout: true).trim()
}
