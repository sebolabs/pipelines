@Library('MySharedLibraries')
import sebolabs.service.Globals
import sebolabs.service.Tools
def Globals = new Globals()
def Tools   = new Tools()
//----------------- INPUT PARAMS ---------------------
String environment      = "${ENVIRONMENT}"
String frontend_branch  = "${FRONTEND_BRANCH}"
String backend_branch   = "${BACKEND_BRANCH}"
String terraform_branch = "${TERRAFORM_BRANCH}"
//----------------- FIXED PARAMS ---------------------
Map tf_params = [
  component: 'service',
  gate_on: true,
  gate_wait_min: 5,
  env_bucket_name: "my_bucket_prefix.service.${ENVIRONMENT}",
  env_bucket_tf_resource_name: 'module.service.aws_s3_bucket.main'
]
Map build_dirs = [
  packages: 'upload/packages',
  assets:   'upload/assets',
  manifest: 'upload/manifests'
]
//--------- SERVICE SPECIFIC REPOS MAP --------------
Map repos = [
  terraform: [
    url: 'git@gitlab.local:sebolabs/terraform.git',
    branch: terraform_branch,
    path: 'terraform',
    credentials: Globals.GITLAB_CREDS
  ],
  frontend: [
    url: 'https://github.com/my_service/frontend.git',
    branch: frontend_branch,
    path: 'frontend',
    projects: [
      frontend: [
        path: '',
        lambda_s3_key: 'frontend',
        is_web_app: true,
        test: [
          env_vars: 'APPSECRET=12345'
        ]
      ]
    ]
  ],
  backend: [
    url: 'git@gitlab.local:sebolabs/backend.git',
    branch: backend_branch,
    path: 'backend',
    credentials: Globals.GITLAB_CREDS,
    projects: [
      transcode_service: [
        path: 'transcode-service',
        lambda_s3_key: 'transcode_service'
      ],
      verify_service: [
        path: 'verify-service',
        lambda_s3_key: 'verify_service'
      ],
      upload_service: [
        path: 'upload-service',
        lambda_s3_key: 'upload_service'
      ]
    ]
  ]
]
//---------- GLOBAL VARIABLES DECLARATIONS ------------
String manifest_version
String manifest_path
Map checkout_steps  = [:]
Map app_test_steps  = [:]
Map app_build_steps = [:]
Map upload_steps    = [:]
//--------------------- FIRE! -------------------------
node('slave') {
  env.WSPACE = pwd()
    
  // generate manifest file version
  manifest_version = new Date().format('yyyyMMddHHmmss', TimeZone.getTimeZone('UTC')).toString()+("_${env.BUILD_NUMBER}")

  stage('prepare') {
    // prepare workspace
    build_dirs.each { build_dir -> sh "mkdir -p ${env.WSPACE}/${build_dir.value}" }
    manifest_path = "${env.WSPACE}/${build_dirs['manifest']}/manifest_${manifest_version}.tfvars"

    // prepare CI stages
    repos.eachWithIndex { the_repo, repoIndex ->
      def repo = the_repo
      Tools.log('info', "> Preparing #${repoIndex+1} repo: ${repo.key}")

      // checkout
      checkout_steps[repo.key] = {
        Tools.git_check_out(
          repo.value['url'],
          repo.value['branch'],
          repo.value['path'],
          repo.value['has_submodules'] ?: false,
          repo.value['credentials'] ?: null
        )
      }

      if(repo.value['projects']) {
        repo.value['projects'].eachWithIndex { project, projectIndex ->
          def proj = project
          Tools.log('info', ">> Processing #${repoIndex+1}.${projectIndex+1} project: ${proj.key}")

          // test
          app_test_steps[proj.key] = {
            String env_vars
            if(proj.value['test']) { env_vars = proj.value['test'].env_vars ?: env_vars }
            dir("${env.WSPACE}/${repo.value['path']}/${proj.value['path']}") {
              Tools.npm_run('run tests', env_vars)
            }
          }

          // build
          Map packages_info = [:]
          packages_info[proj.key] = [:]
          if(proj.value['is_web_app']) {
            app_build_steps[proj.key] = {
              packages_info[proj.key].packages = Tools.build_express_app(
                "${env.WSPACE}/${repo.value['path']}/${proj.value['path']}",
                proj.value['lambda_s3_key'],
                proj.value['assets_s3_key'] ?: 'assets',
                build_dirs[proj.value['assets_folder']] ?: build_dirs['assets'],
                build_dirs['packages']
              )
              Tools.update_lambda_key(manifest_path, proj.value['lambda_s3_key'], packages_info[proj.key].packages[proj.value['lambda_s3_key']])
              TFFunctions.update_lambda_key(
                manifest_path,
                proj.value['assets_s3_key'] ?: 'assets',
                packages_info[proj.key].packages[proj.value['assets_s3_key']] ?: packages_info[proj.key].packages['assets']
              )
            }
            // upload (assets)
            upload_steps["${proj.key}-assets"] = {
              AwsCliFunctions.s3_sync_folder(
                build_dirs[proj.value['assets_folder']] ?: build_dirs['assets'],
                tf_params['env_bucket_name'],
                proj.value['assets_folder'] ?: 'assets',
                assets_sync_options,
                aws_region
              )
            }
          } else {
            app_build_steps[proj.key] = {
              packages_info[proj.key].packages = Tools.build_nodejs_app(
                "${env.WSPACE}/${repo.value['path']}/${proj.value['path']}",
                proj.value['lambda_s3_key'],
                build_dirs['packages']
              )
              Tools.update_lambda_key(manifest_path, proj.value['lambda_s3_key'], packages_info[proj.key].packages[proj.value['lambda_s3_key']])
            }
          }
        }
      }
    }

    // upload (packages)
    upload_steps['packages'] = {
      Tools.s3_sync_folder(build_dirs['packages'], tf_params['env_bucket_name'], 'packages')
    }
  }

  stage('checkout') {
    parallel(checkout_steps)
  }

  stage('tests') {
    parallel(app_test_steps)
  }

  stage('build') {
    Tools.generate_manifest(tf_params['component'], manifest_path)
    parallel(app_build_steps)
  }

  stage('terraform plan') {
    Tools.terraform_run('plan', "-var-file=${manifest_path}")
  }

  stage('terraform gate') {
    if(tf_params['gate_on']) {
      timeout(tf_params['gate_wait_min']) {
        input message: 'Are you happy with the plan?', ok: 'Yes!'
      }
    }
  }

  stage('verify env s3 bucket') {
    if(!Tools.check_if_s3_bucket_exists(tf_params['env_bucket_name'])) {
      Tools.terraform_run('apply', "-var-file=${manifest_path} --target ${tf_params['env_bucket_tf_resource_name']}")
    }
  }

  stage('upload') {
    parallel(upload_steps)
  }

  stage('terraform apply') {
    Tools.terraform_run('apply', "-var-file=${manifest_path}")
    Tools.s3_sync_folder(build_dirs['manifest'], tf_params['env_bucket_name'], 'manifests')
  }
}
