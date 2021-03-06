//
// Copyright 2015 Skytap Inc.
// 
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

import groovyx.net.http.RESTClient
import groovyx.net.http.HttpResponseException
import groovyx.net.http.ContentType
import com.urbancode.air.AirPluginTool

def apTool = new AirPluginTool(this.args[0], this.args[1])
props = apTool.getStepProperties()
def configID = props['configID']
def vmName = props['vmName']
def networkName = props['networkName']
def portNumber = props['portNumber']
def username = props['username']
def password = props['password']
def proxyHost = props['proxyHost']
def proxyPort = props['proxyPort']
def verboseLogging = props['verboseLogging']

def unencodedAuthString = username + ":" + password
def bytes = unencodedAuthString.bytes
encodedAuthString = bytes.encodeBase64().toString()

println "Create Published Service Request Info:"
println "	Environment ID: " + configID
println "	VM Name: " + vmName
println "	Network Name: " + networkName
println "	Port Number: " + portNumber
println "	Proxy Host: " + proxyHost
println "	Proxy Port: " + proxyPort
println "	Verbose Logging: " + verboseLogging
println "Done"

def skytapRESTClient = new RESTClient('https://cloud.skytap.com/')
skytapRESTClient.defaultRequestHeaders.'Authorization' = 'Basic ' + encodedAuthString
skytapRESTClient.defaultRequestHeaders.'Accept' = "application/json"
skytapRESTClient.defaultRequestHeaders.'Content-Type' = "application/json"
if (proxyHost) {
	if (proxyPort) {
		IDTESRESTClient.setProxy(proxyHost, proxyPort.toInteger(), "http")
	} else {
		println "Error: Proxy Host was specified but no Proxy Port was specified"
		System.exit(1)
	}
}

//
// Get Environment Info and Find VM
//
locked = 1
while (locked == 1) {
	locked = 0
	try {
		response = skytapRESTClient.get(path: "configurations/" + configID)
	} catch (HttpResponseException ex) {
		if (ex.statusCode == 423) {
			println "Environment " + configID + " locked. Retrying..."
			locked = 1
			sleep(5000)
		} else {
			System.err.println "Unexpected Error: " + ex.statusCode + " - " + ex.getMessage()
			System.exit(1)
		}
	}
}

//
// Find VM by name and set vmID
//
vmID = 0
vmList = response.data.vms
vmList.each {
	if (it.name == vmName) {
		println "Found VM Name: " + it.name
		vmID = it.id
		println "VM ID: " + vmID
	}
}

if (vmID == 0) {
	System.err.println "Error: VM with Name \"" + vmName + "\" was not found"
	System.exit(1)
}

//
// Get VM data and find the named network
//
locked = 1
while (locked == 1) {
	locked = 0
	try {
		response = skytapRESTClient.get(path: "configurations/" + configID + "/vms/" + vmID)
	} catch (HttpResponseException ex) {
		if (ex.statusCode == 423) {
			println "Environment " + configID + " locked. Retrying..."
			locked = 1
			sleep(5000)
		} else {
			System.err.println "Unexpected Error: " + ex.statusCode + " - " + ex.getMessage()
			System.exit(1)
		}
	}
}
//
// Find Network by name and set interfaceID
//
interfaceID = 0
interfaceList = response.data.interfaces
interfaceList.each {
	if (it.network_name == networkName) {
		println "Found Network Name: " + it.network_name
		interfaceID = it.id
		println "Interface ID: " + interfaceID
	}
}

if (interfaceID == 0) {
	System.err.println "Error: Network with Name \"" + networkName + "\" was not found"
	System.exit(1)
}


locked = 1
while (locked == 1) {
	locked = 0
	try {
		response = skytapRESTClient.post(path: "configurations/" + configID + "/vms/" + vmID + "/interfaces/" + interfaceID + "/services", 
			query:[port:portNumber],
			requestContentType: ContentType.JSON,
			body: null)
	} catch (HttpResponseException ex) {
		if (ex.statusCode == 423) {
			println "Environment " + configID + " locked. Retrying..."
			locked = 1
			sleep(5000)
		} else {
			System.err.println "Unexpected Error: " + ex.statusCode + " - " + ex.getMessage()
			if (ex.statusCode == 409) {
				System.err.println "Port " + portNumber + " may already have a published service assigned"
			}
			System.exit(1)
		}
	}
}

serviceAddress = response.data.external_ip + ":" + response.data.external_port

println "Setting serviceAddress property for Port " + portNumber + " to: " + serviceAddress
apTool.setOutputProperty("serviceAddress", serviceAddress)
apTool.setOutputProperties()
