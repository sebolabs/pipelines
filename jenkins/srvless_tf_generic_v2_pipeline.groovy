pipelineJob('generic_declarative_pipeline') {
  displayName('Generic Declarative Pipeline')
  description('A generic declarative pipeline for Lambda powered APIs')
  definition {
    cpsScm {
      lightweight(false) // false, otherwise the branch variable won't work
      scm {
        git {
          remote {
            url('https://github.com/sebolabs/pipelines.git')
          }
          branch('$PIPELINE_BRANCH') // no curly brackets, otherwise won't be recognised
        }
      }
      scriptPath('jenkins/srvless_tf_generic_v2.groovy')
    }
  }
  parameters {
    activeChoiceParam('ENVIRONMENT') {
      choiceType('SINGLE_SELECT')
      groovyScript {
        script("return ['INT', 'PREP', 'UAT', 'LIVE'];")
      }
    }
    stringParam('TRANSCODER_BRANCH', 'master', '')
    stringParam('IMAGES_BRANCH', 'master', '')
    stringParam('TERRAFORM_BEANCH', 'terraform-mock', '')
    stringParam('PIPELINE_BRANCH', 'jenkins-pipeline-v2', '')
  }
}
