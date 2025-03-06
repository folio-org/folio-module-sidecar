import org.jenkinsci.plugins.workflow.libs.Library

@Library('folio_jenkins_shared_libs@EPC') _

buildMvn {
  publishModDescriptor = false
  mvnDeploy = true
  buildNode = 'jenkins-agent-java21-bigmem'

  doDocker = {
    buildJavaDocker {
      publishMaster = true
    }
  }
}
