// vim :set ts=2 sw=2 sts=2 expandtab smarttab :
/**
 *  Ecolink Motion Sensor
 *
 *  Copyright 2016 Brian Aker
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */

def getDriverVersion()
{
	return "v1.1"
}

metadata {
	definition (name: "Ecolink Motion Sensor", namespace: "TangentOrgThings", author: "Brian Aker") {
		capability "Battery"
		capability "Configuration"
		capability "Motion Sensor"
		capability "Refresh"
		capability "Sensor"
		capability "Tamper Alert"

		// String attribute with name "firmwareVersion"
		attribute "firmwareVersion", "string"
		attribute "driverVersion", "string"
		attribute "associations", "string"

		// zw:S type:2001 mfr:014A prod:0001 model:0001 ver:2.00 zwv:3.40 lib:06 cc:30,71,72,86,85,84,80,70 ccOut:20
		fingerprint type: "2001", mfr: "014A", prod: "0001", model: "0001", deviceJoinName: "Ecolink Motion Sensor", inClusters: "0x30, 0x71, 0x72, 0x86, 0x85, 0x84, 0x80, 0x70", outClusters: "0x20" // Ecolink motion
		fingerprint mfr: "014A", prod: "0004", model: "0001", deviceJoinName: "Ecolink Motion Sensor"  // Ecolink motion +

	}

	simulator {
		status "inactive": "command: 3003, payload: 00"
		status "active": "command: 3003, payload: FF"
	}

	tiles {
		standardTile("motion", "device.motion", width: 2, height: 2)
		{
			state("active", label:'motion', icon:"st.motion.motion.active", backgroundColor:"#53a7c0")
			state("inactive", label:'no motion', icon:"st.motion.motion.inactive", backgroundColor:"#ffffff")
		}
		valueTile("battery", "device.battery", inactiveLabel: false, decoration: "flat")
		{
			state("battery", label:'${currentValue}% battery', unit:"")
		}
		valueTile("driverVersion", "device.driverVersion", inactiveLabel: false, decoration: "flat") 
		{
			state("driverVersion", label:'${currentValue}')
		}
		standardTile("configure", "device.switch", inactiveLabel: false, decoration: "flat")
		{
			state "default", label:"", action:"configure", icon:"st.secondary.configure"
		}
		standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat")
		{
			state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
		}


		main "motion"
		details(["motion", "battery", "refresh", "configure"])
	}
}

def parse(String description) 
{
	def result = null
	if (description.startsWith("Err")) 
	{
		result = createEvent(descriptionText:description)
	} 
	else 
	{
		def cmd = zwave.parse(description, [0x20: 1, 0x30: 1, 0x70: 2, 0x71: 3, 0x72: 2, 0x80: 1, 0x84: 1, 0x85: 2, 0x86: 1])
		if (cmd)
		{
			result = zwaveEvent(cmd)
		}
		else
		{
			result = createEvent(value: description, descriptionText: description, isStateChange: false)
		}
	}
	return result
}

def sensorValueEvent(value)
{
	if (value)
	{
		createEvent(name: "motion", value: "active", descriptionText: "$device.displayName detected motion")
	}
	else
	{
		createEvent(name: "motion", value: "inactive", descriptionText: "$device.displayName motion has stopped")
	}
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd)
{
	sensorValueEvent(cmd.value)
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd)
{
	sensorValueEvent(cmd.value)
}

def zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd)
{
	sensorValueEvent(cmd.value)
}

def zwaveEvent(physicalgraph.zwave.commands.sensorbinaryv1.SensorBinaryReport cmd)
{
	sensorValueEvent(cmd.sensorValue)
}

def zwaveEvent(physicalgraph.zwave.commands.sensoralarmv1.SensorAlarmReport cmd)
{
	sensorValueEvent(cmd.sensorState)
}

def zwaveEvent(physicalgraph.zwave.commands.notificationv3.NotificationReport cmd)
{
	def result = []
	if (cmd.notificationType == 0x07)
	{
		if (cmd.v1AlarmType == 0x07)
		{  // special case for nonstandard messages from Monoprice ensors
		result << sensorValueEvent(cmd.v1AlarmLevel)
		}
		else if (cmd.event == 0x01 || cmd.event == 0x02 || cmd.event == 0x07 || cmd.event == 0x08)
		{
			result << sensorValueEvent(1)
		}
		else if (cmd.event == 0x00)
		{
			result << sensorValueEvent(0)
		}
		else if (cmd.event == 0x03)
		{
			result << createEvent(name: "tamper", value: "detected", descriptionText: "$device.displayName covering was removed", isStateChange: true)
			result << response(zwave.batteryV1.batteryGet())
		}
		else if (cmd.event == 0x05 || cmd.event == 0x06)
		{
			result << createEvent(descriptionText: "$device.displayName detected glass breakage", isStateChange: true)
		}
	}
	else if (cmd.notificationType)
	{
		def text = "Notification $cmd.notificationType: event ${([cmd.event] + cmd.eventParameter).join(", ")}"
			result << createEvent(name: "notification$cmd.notificationType", value: "$cmd.event", descriptionText: text, isStateChange: true, displayed: false)
	}
	else
	{
		def value = cmd.v1AlarmLevel == 255 ? "active" : cmd.v1AlarmLevel ?: "inactive"
		result << createEvent(name: "alarm $cmd.v1AlarmType", value: value, isStateChange: true, displayed: false)
	}
	result
}

def zwaveEvent(physicalgraph.zwave.commands.wakeupv1.WakeUpNotification cmd)
{
	def result = [createEvent(descriptionText: "${device.displayName} woke up", isStateChange: false)]

	if (state.MSR == "011A-0601-0901" && device.currentState('motion') == null)
	{  // Enerwave motion doesn't always get the associationSet that the hub sends on join
	result << response(zwave.associationV1.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId))
	}
	if (!state.lastbat || (new Date().time) - state.lastbat > 53*60*60*1000)
	{
		result << response(zwave.batteryV1.batteryGet())
	}
	else
	{
		result << response(zwave.wakeUpV1.wakeUpNoMoreInformation())
	}
	result
}

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd)
{
	def map = [ name: "battery", unit: "%" ]
	if (cmd.batteryLevel == 0xFF)
	{
		map.value = 1
		map.descriptionText = "${device.displayName} has a low battery"
		map.isStateChange = true
	}
	else 
	{
		map.value = cmd.batteryLevel
	}
	state.lastbat = new Date().time
	[createEvent(map), response(zwave.wakeUpV1.wakeUpNoMoreInformation())]
}

def zwaveEvent(physicalgraph.zwave.Command cmd) 
{
	createEvent(descriptionText: "$device.displayName: $cmd", displayed: false)
}

def zwaveEvent(physicalgraph.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) 
{
	def result = []

	def msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
	log.debug "msr: $msr"
	updateDataValue("MSR", msr)

	result << createEvent(descriptionText: "$device.displayName MSR: $msr", isStateChange: false)
	result
}

def zwaveEvent(physicalgraph.zwave.commands.associationv1.AssociationReport cmd) 
{
	def result = []
	if (cmd.nodeId.any { it == zwaveHubNodeId }) 
	{
		def string_of_assoc
		cmd.nodeId.each {
			string_of_assoc << "${it}, "
		}
		def lengthMinus2 = string_of_assoc.length() - 2
		def final_string = string_of_assoc.getAt(0..lengthMinus2)
		result << createEvent(name: "associations", value: "$final_string", descriptionText: "$device.displayName is associated in group ${cmd.groupingIdentifier} : $final_string")
	}
	else if (cmd.groupingIdentifier == 2)
	{
		result << response(zwave.associationV1.associationSet(groupingIdentifier:cmd.groupingIdentifier, nodeId:zwaveHubNodeId))
			result << response(zwave.associationV1.associationGet(groupingIdentifier:2))
	}
	else
	{
		result << createEvent(descriptionText: "$device.displayName lacks a group 1, this is an error")
	}
	result
}

def refresh()
{
	log.debug "refresh() is called"

	def commands = [
	zwave.switchBinaryV1.switchBinaryGet().format(),
	zwave.batteryV1.batteryGet().format(),
	zwave.associationV1.associationGet(groupingIdentifier:1).format()
	]
	if (getDataValue("MSR") == null)
	{
		commands << zwave.manufacturerSpecificV1.manufacturerSpecificGet().format()
	}
	if (device.currentState('firmwareVersion') == null)
	{
		commands << zwave.versionV1.versionGet().format()
	}
	delayBetween(commands, 6000)
}

// handle commands
def configure()
{
	refresh()
}
