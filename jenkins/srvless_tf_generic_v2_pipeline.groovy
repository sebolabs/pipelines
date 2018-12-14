pipelineJob('generic_declarative_pipeline') {
  displayName('Generic Declarative Pipeline')
  definition {
    cps {
      script(readFileFromWorkspace('jenkins/dummy.groovy'))
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
  }
}
