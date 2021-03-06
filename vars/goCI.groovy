#!/usr/bin/groovy
import io.fabric8.Fabric8Commands
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()


    def ghOrg =  config.githubOrganisation
    def dockerOrg = config.dockerOrganisation
    def prj = config.project
    def buildOptions = config.dockerBuildOptions ?: ''
    
    def flow = new Fabric8Commands()
    def version
    def imageName 

    if (!ghOrg){
        error 'no github organisation defined'
    }
    if (!dockerOrg){
        error 'no docker organisation defined'
    }
    if (!prj){
        error 'no project defined'
    }

    def buildPath = "/home/jenkins/go/src/github.com/${ghOrg}/${prj}"

    sh "mkdir -p ${buildPath}"

    dir(buildPath) {
        checkout scm

        container(name: 'go') {
            stage ('build binary'){
                version = "SNAPSHOT-${env.BRANCH_NAME}-${env.BUILD_NUMBER}"
                sh "make"
            }
        }

        container(name: 'docker') {
            imageName = "docker.io/${dockerOrg}/${prj}"

            stage ('build snapshot image'){
                sh "docker build -t ${imageName}:${version} ${buildOptions} ."
            }

            stage ('push snapshot image'){
                sh "docker push ${imageName}:${version}"
            }
        }

        stage('notify'){
            def changeAuthor = env.CHANGE_AUTHOR
            if (!changeAuthor){
                error "no commit author found so cannot comment on PR"
            }
            def pr = env.CHANGE_ID
            if (!pr){
                error "no pull request number found so cannot comment on PR"
            }
            def message = "@${changeAuthor} snapshot ${prj} image is available for testing.  `docker pull ${imageName}:${version}`"
            container('docker'){
                flow.addCommentToPullRequest(message, pr, "${ghOrg}/${prj}")
            }
        }
    }
    return version
  }