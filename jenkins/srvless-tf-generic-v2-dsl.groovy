pipelineJob('GenericDeclarativePipeline') {
  displayName('Generic Declarative Pipeline')
  scm {
    git{
      remote {
        url('https://github.com/sebolabs/pipelines.git')
        branch('jenkins-pipeline-v2')
      }
    }
  }
  steps {
    dsl {
      external('jenkins/srvless-tf-generic-v2-dsl.groovy')
      removeAction('DELETE')
      removeViewAction('DELETE')
    }
  }
  parameters {
    activeChoiceParam('ENVIRONMENT') {
      choiceType('SINGLE_SELECT')
      groovyScript {
          script("return ['INT', 'PREP', 'UAT', 'LIVE']")
      }
    }
    stringParam('TRANSCODER_BRANCH', 'master', '')
    stringParam('IMAGES_BRANCH', 'master', '')
    stringParam('TERRAFORM_BEANCH', 'terraform-mock', '')
  }
}
