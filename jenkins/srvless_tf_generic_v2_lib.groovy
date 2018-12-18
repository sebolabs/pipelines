void gitCheckOutRepo(String repo_url, String repo_branch, String target_dir, String creds = null, Boolean poll = false, Boolean shallow = true) {
  checkout poll: poll, scm: [
    $class: 'GitSCM',
    branches: [[
      name: repo_branch
    ]],
    doGenerateSubmoduleConfigurations: false,
    extensions: [[
      $class: 'CloneOption',
      depth: 0,
      noTags: true,
      reference: '',
      shallow: shallow
      ],[
      $class: 'RelativeTargetDirectory',
      relativeTargetDir: target_dir
    ]],
    submoduleCfg: [],
    userRemoteConfigs: [[
      credentialsId: creds,
      url: repo_url
    ]]
  ]
}

void log(String level, String message) {
  String label
  String colour
  String reset = '\u001B[0m'

  switch (level) {
    case 'info':
      label  = 'INFO'
      colour = '\u001B[32m' // Green
      break
    case 'warn':
      label  = 'WARN'
      colour = '\u001B[33m' // Yellow
      break
    case 'error':
      label  = 'ERROR'
      colour = '\u001B[31m' // Red
      break
  }
  println("${colour}[${label}] ${message}${reset}")
}

void shout(String text, String colour_code, String header, Integer width) {
  String lines            = ('='.multiply(width)).toString()
  Integer header_position = (width/2 - header.length()/2)
  String header_margin    = (' '.multiply(header_position)).toString()
  println "\u001B[${colour_code}${lines}\n${header_margin}${header}\n${lines}\n${text}\n${lines}\u001B[0m"
}

String gitCheckFolderLatestCommit(String cwd) {
  String commit
  dir(cwd) {
    commit = sh(
      script: 'git log --pretty=format:"%H" -1',
      returnStdout: true
    ).trim()
  }
  return commit
}

String buildZipPackage(String cwd, String package_name_stam, String dst_folder) {
  dir(cwd) {
    // build
  }
  // return "${package_name_stam}_${version}.zip"
}

void testsRun(String cwd, String test_type) {
  dir(cwd) {
    // test
  }
}

String generateManifestVersion() {
  // generate
  // return version
}

String obtainManifests(String s3_bucket_name, String path) {
  String latest_manifest  = 'none'
  Boolean manifest_exists = // compute
  if(manifest_exists) {
    latest_manifest = // compute
  }
  return latest_manifest
}

void jsonPutKey(String option, def json, def key, def value) {
  switch (option) {
    case 'add':
      if(!json.containsKey(key) && value != null) {
        json[key] = value
      }
      break
    case 'replace':
      if(value != null) {
        json[key] = value
      }
      break
  }
}

void jsonRemoveAllKeysByKeyName(def json, String key) {
  json.remove(key)
  json.each { j ->
    if(j.value instanceof net.sf.json.JSONObject) {
      jsonRemoveAllKeysByKeyName(j.value, key)
    }
  }
}

void manifestCleanUp(def json, def old_keys, def current_keys) {
  def diff = old_keys - current_keys
  if(diff.size() > 0) {
    log('warn', "MANIFEST KEYS TO BE REMOVED: ${diff}")
    diff.each { jsonRemoveAllKeysByKeyName(json, it) }
  }
}

void manifestGenerate(String bucket_name, def manifest_template, def latest_manifest, String manifest_json_path, String dst_path) {
  if(latest_manifest != 'none') {
    // download latest manifest
  } else {
    // create new manifest from template
  }
  shout(JsonOutput.prettyPrint(readFile(manifest_json_path)), '35m', 'INITIAL MANIFEST CONTENT', 80)
}
