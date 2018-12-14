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
      external('jenkins/srvless_tf_generic_v2_dsl.groovy')
      removeAction('DELETE')
      removeViewAction('DELETE')
    }
  }
  parameters {
    choiceParam('ENVIRONMENT', ['INT', 'PREP', 'UAT', 'LIVE'])
    stringParam('TRANSCODER_BRANCH', 'master', '')
    stringParam('IMAGES_BRANCH', 'master', '')
    stringParam('TERRAFORM_BEANCH', 'terraform-mock', '')
  }
}
