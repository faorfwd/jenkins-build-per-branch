package com.entagen.jenkins

import java.util.regex.Pattern

class GitApi {
    String gitUrl
	String gitBaseBranch = "development"
    Pattern branchNameFilter = null

    public List<String> getBranchNames() {
        String command = "git ls-remote --heads ${gitUrl}"
        List<String> branchNames = []

        eachResultLine(command) { String line ->
            String branchNameRegex = "^.*\trefs/heads/(.*)\$"
            String branchName = line.find(branchNameRegex) { full, branchName -> branchName }
            Boolean selected = passesFilter(branchName)
            println "\t" + (selected ? "* " : "  ") + "$line"
            // lines are in the format of: <SHA>\trefs/heads/BRANCH_NAME
            // ex: b9c209a2bf1c159168bf6bc2dfa9540da7e8c4a26\trefs/heads/master
            if (selected) branchNames << branchName
        }

		if(gitBaseBranch != null) {
			String uuid = UUID.randomUUID() as String
			// clone the target repo in order to get access to the merged state
			
			def cloneProcess = "git clone -b ${gitBaseBranch} ${gitUrl} ${uuid}".execute()
			cloneProcess.waitFor()
			
			if(cloneProcess.exitValue() == 0) {
				// check the list of merged branches into the base one
				command = "git -C ${uuid} branch -r --merged \"${gitBaseBranch}\""
				
				eachResultLine(command) { String line -> 
					String branchNameRegex = "^.*origin/(.*)\$"
					String branchName = line.find(branchNameRegex) { full, branchName -> branchName }
					Boolean deselected = branchName != gitBaseBranch && branchNames.contains(branchName)
					if(deselected) {
						println "REMOVING $branchName as merged into $gitBaseBranch"
						branchNames -= [branchName]
					}
				}
				
				def subDir = new File(uuid)
				subDir.deleteDir()
			} else {
				String errorText = cloneProcess.errorStream.text?.trim()
				println "error executing command: $command"
				println errorText
			}
		}
        return branchNames
    }

    public Boolean passesFilter(String branchName) {
        if (!branchName) return false
        if (!branchNameFilter) return true
        return branchName ==~ branchNameFilter
    }

    // assumes all commands are "safe", if we implement any destructive git commands, we'd want to separate those out for a dry-run
    public void eachResultLine(String command, Closure closure) {
        println "executing command: $command"
        def process = command.execute()
        def inputStream = process.getInputStream()
        def gitOutput = ""

        while(true) {
          int readByte = inputStream.read()
          if (readByte == -1) break // EOF
          byte[] bytes = new byte[1]
          bytes[0] = readByte
          gitOutput = gitOutput.concat(new String(bytes))
        }
        process.waitFor()

        if (process.exitValue() == 0) {
            gitOutput.eachLine { String line ->
               closure(line)
          }
        } else {
            String errorText = process.errorStream.text?.trim()
            println "error executing command: $command"
            println errorText
            throw new Exception("Error executing command: $command -> $errorText")
        }
    }

}
