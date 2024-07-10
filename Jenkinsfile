import org.folio.eureka.EurekaImage
import org.jenkinsci.plugins.workflow.libs.Library

@Library('pipelines-shared-library@RANCHER-1576') _
node('jenkins-agent-java17') {
  stage('Build Docker Image') {
    dir('folio-module-sidecar') {
      EurekaImage image = new EurekaImage(this)
      image.setModuleName('folio-module-sidecar')
      image.setBranch('EUREKA-210')
      image.makeImage()
    }
  }
}
buildMvn {
  publishModDescriptor = false
  mvnDeploy = true
  buildNode = 'jenkins-agent-java17'
}
