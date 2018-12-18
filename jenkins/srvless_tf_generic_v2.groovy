#!groovy
@Library('MySharedLibraries')
import groovy.json.JsonOutput
import sebolabs.Functions
Functions fn = new Functions()
//--------------------------------------------------------------------------------------------------
Integer to_build = 0
String manifest_local_path
String artefacts_s3_bucket          = fn.ARTEFACTS_S3_BUCKET
List terraform_lambda_s3_keys       = []
Map<String,String> working_dirs     = [manifests: "${ENVIRONMENT}/manifests", packages: 'packages']
Map<String,Boolean> build_plan      = [:] // what to build
Map<String,Closure<?>> test_steps   = [:] // test stage steps
Map<String,Closure<?>> build_steps  = [:] // build stage steps
Map<String,Closure<?>> upload_steps = [:] // upload stage steps
//--------------------------------------------------------------------------------------------------
pipeline {
  agent any

  options {
    ansiColor('xterm')
    timestamps()
    disableConcurrentBuilds()
  }

  stages {
    stage('checkout') {
      failFast true
      steps {
        script {
          fn.log('info', 'CHECKOUT STAGE')

          Map checkout_steps = [:]
          def repos = readJSON(file: 'repos.json')
          repos.each { the_repo ->
            def repo = the_repo

            // generate checkout steps
            checkout_steps[repo.key] = {
              fn.gitCheckOutRepo(
                repo.value.url,
                repo.value.branch,
                repo.value.dst_path,
                repo.value.credentials ?: null
              )
            }
          }
          parallel(checkout_steps)
        }
      }
    }

    stage('prep') {
      failFast true
      steps {
        script {
          fn.log('info', 'PREP STAGE')

          // prepare workspace
          working_dirs.each { dir -> fn.createDirectory(dir.value) }

          // prepare manifests
          String manifest_version = fn.generateManifestVersion()
          manifest_local_path = "${working_dirs.manifests}/manifest_${manifest_version}.json"
          String latest_manifest = fn.obtainManifests(artefacts_s3_bucket, working_dirs.manifests)
          fn.manifestGenerate(artefacts_s3_bucket, readJSON(file: 'manifest.template'), latest_manifest, manifest_local_path, working_dirs.manifests)
          def manifest_json = readJSON(file: manifest_local_path)

          // add release and deployment information
          fn.jsonPutKey('replace', manifest_json, 'release', manifest_version)
          fn.jsonPutKey('replace', manifest_json.deployments, params.ENVIRONMENT, getTimeStamp('yyyy-MM-dd HH:mm'))

          // iterate over repos
          def repos = readJSON(text: repos_json)
          repos.eachWithIndex { the_repo, repoIndex ->
            def repo = the_repo
            fn.log('info', "> Preparing #${repoIndex+1} repo: ${repo.key}")

            // iterate over repo.projects
            if(repo.value.projects) {
              repo.value.projects.eachWithIndex { the_project, projectIndex ->
                def project = the_project
                fn.log('info', ">> Processing #${repoIndex+1}.${projectIndex+1} project: ${project.key}")

                // populate microservice project keys
                fn.jsonPutKey('add', manifest_json.microservices, project.key, [:])
                fn.jsonPutKey('replace', manifest_json.microservices[project.key], 'terraform', project.value.terraform)

                // save current state of terraform lambda s3 keys vars
                terraform_lambda_s3_keys.add(project.value.terraform.lambda_s3_key_var)

                // prepare build plan
                String current_commit = fn.gitCheckFolderLatestCommit("${repo.value.dst_path}/${project.value.src_path}")
                build_plan[project.key] = !current_commit.equals(manifest_json.microservices[project.key].commit.toString())

                // generate steps for other stages
                if(build_plan[project.key]) {
                  to_build += 1

                  // update project with commit hash
                  fn.jsonPutKey('replace', manifest_json.microservices[project.key], 'commit', current_commit)

                  // generate test steps
                  test_steps[project.key] = { fn.testsRun("${repo.value.dst_path}/${project.value.src_path}", 'unit') }

                  // generate build steps
                  Map build_artefacts = [:]
                  build_steps[project.key] = {
                    build_artefacts[project.key] = fn.buildZipPackage(
                      "${repo.value.dst_path}/${project.value.src_path}",
                      project.key,
                      working_dirs.packages
                    )
                    // update terraform variable with package name
                    fn.jsonPutKey('replace', manifest_json, project.value.terraform.lambda_s3_key_var, build_artefacts[project.key].toString())
                    writeFile(file: manifest_local_path, text: manifest_json.toString())
                  }
                }
              } //repo.value['projects'].eachWithIndex
            } //if(repo.value['projects'])
          } //repos.eachWithIndex

          // generate upload steps
          working_dirs.each { dir -> upload_steps[dir.value] = { fn.s3SyncFolder(artefacts_s3_bucket, dir.value) } }

        } //script
      } //steps
    } //stage

    stage('test') {
      failFast true
      when { not { equals expected: 0, actual: to_build } }
      steps {
        script {
          fn.log('info', 'TEST STAGE')
          parallel(test_steps)
        }
      }
    }

    stage('build') {
      failFast true
      when { not { equals expected: 0, actual: to_build } }
      steps {
        script {
          fn.log('info', 'BUILD STAGE')
          parallel(build_steps)
        }
      }
    }

    stage('upload') {
      failFast true
      when { not { equals expected: 0, actual: to_build } }
      steps {
        script {
          fn.log('info', 'UPLOAD STAGE')
          // clean up manifest
          def manifest_json = readJSON(file: manifest_local_path)
          fn.manifestCleanUp(manifest_json, manifest_json.keySet().findAll { it.endsWith('_s3_key') }.each{}, terraform_lambda_s3_keys)
          fn.manifestCleanUp(manifest_json, manifest_json.microservices.keySet(), build_plan.keySet())
          writeFile(file: manifest_local_path, text: manifest_json.toString())
          // upload
          parallel(upload_steps)
        }
      }
    }

    stage('deploy') {
      failFast true
      steps {
        script {
          fn.log('info', 'DEPLOY STAGE')
          println '''
            For self implementation... (can also be conditional)
            In general: $ terraform plan/apply -var environment=${ENVIRONMENT} -var-file=${manifest_local_path}
          '''
        }
      }
    }

    // ... other stages

  } //stages

  post {
    always {
      fn.shout(JsonOutput.prettyPrint(readFile(file: manifest_local_path)), '35m', 'THE MANIFEST', 80)
    }
    success {
      println 'SUCCESSFUL RUN :)'
    }
    failure {
      println 'FAILED RUN :('
    }
    cleanup {
      deleteDir()
    }
  } //post
} //pipeline
