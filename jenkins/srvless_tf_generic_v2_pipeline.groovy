pipelineJob('generic_declarative_pipeline') {
  displayName('Generic Declarative Pipeline')
  description('A generic declarative pipeline for Lambda powered APIs')
  definition {
    scm {
      git {
        remote {
          url('https://github.com/sebolabs/pipelines.git')
          branch('${PIPELINE_BRANCH}') // TODO: change to variable?
        }
      }
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
