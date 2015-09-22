import com.urbancode.air.AirPluginTool
import groovyx.net.http.RESTClient
import groovyx.net.http.ContentType

def apTool = new AirPluginTool(this.args[0], this.args[1])
props = apTool.getStepProperties()
def configID = props['configID']
def VMName = props['VMName']
def userDataString = props['userDataString']
def username = props['username']
def password = props['password']

def unencodedAuthString = username + ":" + password
def bytes = unencodedAuthString.bytes
encodedAuthString = bytes.encodeBase64().toString()

println "Set Virtual Machine User Data Command Info:"
println "	Environment ID: " + configID
println "	VM Name: " + VMName
println "	User Data String: " + userDataString
println "Done"

def skytapRESTClient = new RESTClient('https://cloud.skytap.com/')
skytapRESTClient.defaultRequestHeaders.'Authorization: Basic' = encodedAuthString
skytapRESTClient.defaultRequestHeaders.'Accept' = "application/json"
skytapRESTClient.defaultRequestHeaders.'Content-Type' = "application/json"

response = skytapRESTClient.get(path: "configurations/" + configID)

vmID = 0
vmList = response.data.vms

vmList.each {
	if (it.name == VMName) {
		println "Found VM Name: " + it.name
		vmID = it.id
	}
}

if (vmID == 0) {
	System.err.println "Error: VM Name \"" + VMName + "\" not found"
	System.exit(1)
}

println "VM ID: " + vmID

response = skytapRESTClient.post(path: "configurations/" + configID + "/vms/" + vmID + "/user_data.json" ,
	body: ['contents':userDataString],
	requestContentType: ContentType.JSON)

 println "Set User Data to " + userDataString