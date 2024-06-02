#!groovy

def buildFronetndApplication (String app) {
    dir("angular/") { 
        sh "npx nx run-many --target=test --projects=${app} --parallel=true --maxParallel=3" 
        sh "npx nx run-many --target=build --projects=${app} --parallel=true --maxParallel=3 --prod"
        sh "echo \"<pre>Jira version: $VERSION\n${git.getLastCommitInfo()}</pre>\" > ../test_project_${app}/frontend/version.html"
    }                  
}

def buildBackendApplication (String app) {
    sh "mvn clean package -pl test_project_${app} -am -P production"
}

def getValidationSchemas (String app) {
    def validationSchemesDir = "test_project_${app}/target/distrib/validation_schemes"
    sh "mkdir $validationSchemesDir"
    sh "cp -r --force test_project_module 1/common/${app}/validation_schemes/* $validationSchemesDir/"
}

def getExportMapping (String app) {
    def exportMappingsDir = "test_project_${app}/target/distrib/export_mapping"
    sh "mkdir $exportMappingsDir"
    sh "cp -r --force test_project_module 2/${app}/export_mapping/* $exportMappingsDir/"
}

def writeMD5SUMMfiles (String app) {
    sh "mkdir test_project_$app/${TARGET}/checkSums"
    sh "md5sum `find test_project_$app/${TARGET}/ -type f -name *.jar` | awk -F 'test_project_$app/${TARGET}/' '{ print \$1\"\t\"\$2 }' >> test_project_$app/${TARGET}/checkSums/backendMD5"
    sh "md5sum `find test_project_$app/${TARGET}/frontend/ -type f` | awk -F 'test_project_$app/${TARGET}/' '{ print \$1\"\t\"\$2 }' >> test_project_$app/${TARGET}/checkSums/frontendMD5"
    sh "md5sum `find test_project_$app/${TARGET}/ -type f -name *.sh` | awk -F 'test_project_$app/${TARGET}/' '{ print \$1\"\t\"\$2 }' >> test_project_$app/${TARGET}/checkSums/shMD5"
    sh "md5sum `find test_project_$app/${TARGET}/ -type f -name *.json` | awk -F 'test_project_$app/${TARGET}/' '{ print \$1\"\t\"\$2 }' >> test_project_$app/${TARGET}/checkSums/validationMD5"
    sh "md5sum `find test_project_$app/${TARGET}/ -type f -name *.jasper` | awk -F 'test_project_$app/${TARGET}/' '{ print \$1\"\t\"\$2 }' >> test_project_$app/${TARGET}/checkSums/jasperMD5"
}

def getTemplates (String app) {
    sh "cp -r --force test_project_${app}/templates/* test_project_${app}/target/distrib/templates/"
}

def cleanApplication () {
    echo "Clean node_modules and package-lock.json in angular folder's"
    def dirs = [
        "test_project_module 1/angular",
        "test_project_module 2/angular",
        "test_project_module 3/angular",
        "test_project_module 4/angular",
        "test_project_module 5/angular",
        "test_project_module 6/angular",
        "test_project_module 7/angular",
        "test_project_module 8/angular",
        "angular/"
    ]

    dirs.each{ dir -> sh "rm -rf test_project/$dir/{node_modules,package-lock.json}"}

    sh(script: "npm cache clean --force")
}

def deployAllApplications (String path_to, String environment) {
    echo "Deploy all applications"
    deployApplication("module 0", "$path_to", "$environment")
    deployApplication("module 0.0", "$path_to", "$environment")
    
     if (params.MODULE 1) {
	 	deployApplication("module 1", "$path_to", "$environment")
     }
    if (params.MODULE 2) {
        deployApplication("module 2", "$path_to", "$environment")
    }
    if (params.MODULE 3) {
        deployApplication("module 3", "$path_to", "$environment")
    }
    if (params.MODULE 4) {
    	deployApplication("module 4", "$path_to", "$environment")
    }
    if (params.MODULE 5) {
        deployApplication("module 5", "$path_to", "$environment")
    }
}

def deployApplication (String app, String path_to, String environment) {
    echo "Deploy $app"
    sshPublisher(publishers: [sshPublisherDesc(
                            configName: "$environment",
                            transfers: [
                                // remove previous build
                                sshTransfer(
                                    excludes: '',
                                    execCommand: "rm -r --force ~/$path_to/$app/{lib,frontend,validation_schemes,export_mapping,templates}",
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
                                    excludes: "test_project_$app/target/distrib/resources/,test_project_$app/target/distrib/${app}.sh",
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

def publishAllApplications (String app) {
    echo "Deploy all applications"
    publishApplication("module 0")
    publishApplication("module 0.0")
    if (params.MODULE 1) { 
        publishApplication("module 1")
    }
    if (params.MODULE 2) {
        publishApplication("module 2")
    }
    if (params.MODULE 3) {
        publishApplication("module 3")
    }
    if (params.MODULE 4) {
        publishApplication("module 4")
    }
    if (params.MODULE 5) {
        publishApplication("module 5")
    }
}

def publishApplication (String app) {
    // remove previous build for chosen version
    echo "Send distrib $app"
        sshPublisher(publishers: [sshPublisherDesc(
                configName: 'distrib',
                transfers: [
                    sshTransfer (
                        excludes: '',
                        execCommand: "rm -r --force /var/www/distrib/$PROJECT_NAME/distrib/$VERSION/$app",
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
                    // publish distrib modules
                    sshTransfer (
                        excludes: '',
                        execCommand: "chmod +x /var/www/distrib/$PROJECT_NAME/distrib/$VERSION/$app/${app}.sh",
                        execTimeout: 120000,
                        flatten: false,
                        makeEmptyDirs: false,
                        noDefaultExcludes: false,
                        patternSeparator: '[, ]+',
                        remoteDirectory: "$PROJECT_NAME/distrib/$VERSION/${app}",
                        remoteDirectorySDF: false,
                        removePrefix: "test_project_$app/target/distrib/",
                        sourceFiles: "test_project_$app/target/distrib/"
                )],
                usePromotionTimestamp: false,
                useWorkspaceInPromotion: false,
                verbose: false)])       
}
