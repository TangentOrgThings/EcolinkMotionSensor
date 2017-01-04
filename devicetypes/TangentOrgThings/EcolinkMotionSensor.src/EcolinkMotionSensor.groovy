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
  return "v2.1"
}

metadata {
  definition (name: "Ecolink PIR", namespace: "TangentOrgThings", author: "Brian Aker")
  {
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
    attribute "BasicReport", "enum", ["Unconfigured","On","Off"]

    // zw:S type:2001 mfr:014A prod:0001 model:0001 ver:2.00 zwv:3.40 lib:06 cc:30,71,72,86,85,84,80,70 ccOut:20
    fingerprint mfr: "014A", prod: "0001", model: "0001", deviceJoinName: "Ecolink Motion Sensor", inClusters: "0x30, 0x71, 0x72, 0x86, 0x85, 0x84, 0x80, 0x70", outClusters: "0x20" // Ecolink motion
    fingerprint mfr: "014A", prod: "0004", model: "0001", deviceJoinName: "Ecolink Motion Sensor"  // Ecolink motion +
  }

  simulator
  {
    status "inactive": "command: 3003, payload: 00"
    status "active": "command: 3003, payload: FF"
  }

  tiles
  {
    standardTile("motion", "device.motion", width: 2, height: 2)
    {
      state("active", label:'motion', icon:"st.motion.motion.active", backgroundColor:"#53a7c0")
      state("inactive", label:'no motion', icon:"st.motion.motion.inactive", backgroundColor:"#ffffff")
    }
    
    valueTile("battery", "device.battery", inactiveLabel: false, decoration: "flat")
    {
      state("battery", label:'${currentValue}% battery', unit:"")
    }
    
    valueTile("driverVersion", "device.driverVersion", inactiveLabel: true, decoration: "flat") 
    {
      state("driverVersion", label:'${currentValue}')
	}
    
    valueTile("tamper", "device.tamper", inactiveLabel: true, decoration: "flat") 
    {
      state("tamper", label:'${currentValue}')
	}
    
    standardTile("configure", "device.switch", inactiveLabel: false, decoration: "flat")
    {
      state "default", label:"", action:"configuration.configure", icon:"st.secondary.configure"
    }
    
    standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat")
    {
      state "default", label:'', action: "refresh.refresh", icon: "st.secondary.refresh"
    }

    main "motion"
    details(["motion", "battery", "tamper", "driverVersion", "configure", "refresh"])
  }
}

def parse(String description) {
  def result = null

  if (description.startsWith("Err")) {
    results << createEvent(descriptionText:description, displayed:true)
    return results
  } 
    
  def cmd = zwave.parse(description, [0x20: 1, 0x30: 1, 0x70: 1, 0x71: 2, 0x72: 1, 0x80: 1, 0x84: 2, 0x85: 2, 0x86: 1])
	
  if (cmd) {
    result = zwaveEvent(cmd)

    if (!result) {
      log.warning "Parse Failed and returned ${result} for command ${cmd}"
      result = createEvent(value: description, descriptionText: description)
    } else {
      log.debug "Successfull ${result} for command ${cmd}"
    }
  } 
  else 
  {
    log.info "Non-parsed event: ${description}"
    result = createEvent(value: description, descriptionText: description)
  }
    
  return result
}

def sensorValueEvent(short value) {
  def result = []
  
  if (value) {
    result << createEvent(name: "motion", value: "active", descriptionText: "$device.displayName detected motion", isStateChange: true, displayed: true)
  } else {
    result << createEvent(name: "motion", value: "inactive", descriptionText: "$device.displayName motion has stopped", isStateChange: true, displayed: true)
  }
   
  result
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {
  log.info "$device.displayName  BasicReport : ${cmd.value}"
  sensorValueEvent(cmd.value)
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd) {
  log.info "$device.displayName BasicSet : ${cmd.value}"
  sensorValueEvent(cmd.value)
}

def zwaveEvent(physicalgraph.zwave.commands.sensorbinaryv1.SensorBinaryReport cmd) {
  log.info "$device.displayName SensorBinaryReport : ${cmd.sensorValue}"
  sensorValueEvent(cmd.sensorValue)
}

def zwaveEvent(physicalgraph.zwave.commands.alarmv2.AlarmReport cmd, results) {
  def result = []
  if (cmd.alarmLevel == 0x11) {
    result << createEvent(name: "tamper", value: "detected", descriptionText: "$device.displayName covering was removed", isStateChange: true, displayed: true)
  } else {
    result << createEvent(name: "tamper", value: "clear", descriptionText: "$device.displayName is clear", isStateChange: true, displayed: true)
  }
  
  return result
}

def zwaveEvent(physicalgraph.zwave.commands.wakeupv2.WakeUpNotification cmd) {
  def result = [createEvent(descriptionText: "${device.displayName} woke up", isStateChange: false)]
  
  if (state.tamper == "clear") {
  	result << createEvent(name: "tamper", value: "clear", descriptionText: "$device.displayName is clear", isStateChange: true, displayed: true)
  }

  // If the device is in the process of configuring a newly joined network, do not send wakeUpnoMoreInformation commands
  if (isConfigured()) {
    if (!state.lastbat || (new Date().time) - state.lastbat > 53*60*60*1000) {
      result << response(zwave.batteryV1.batteryGet())
    } else {
      result << response(zwave.wakeUpV1.wakeUpNoMoreInformation())
    }
  }
  
  return result
}

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) {
  def map = [ name: "battery", unit: "%" ]
  
  if (cmd.batteryLevel == 0xFF) {
    map.value = 1
    map.descriptionText = "${device.displayName} has a low battery"
    map.isStateChange = true
    map.displayed = true
  } else {
  	map.value = batteryLevelcmd.batteryLevel
  
    if (state.previous_batteryLevel != batteryLevelcmd.batteryLevel) {
      state.previous_batteryLevel = batteryLevelcmd.batteryLevel
      map.isStateChange = true
      map.displayed = true
    }
    map.descriptionText = "${device.displayName} is at ${batteryLevelcmd.batteryLevel}%"
  }
  
  state.lastbat = new Date().time
  
  def result = [createEvent(map)]
  
  if (state.BasicReport != "On") {
  	result = [result, response(zwave.configurationV1.configurationSet(parameterNumber: 0x63, configurationValue: 0xFF, size: 1))]
  }
  
  return result
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
  createEvent(descriptionText: "$device.displayName command not implemented: $cmd", displayed: true)
}

def zwaveEvent(physicalgraph.zwave.commands.manufacturerspecificv1.ManufacturerSpecificReport cmd) {
  def msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
  log.debug "msr: $msr"
  updateDataValue("MSR", msr)

  createEvent(descriptionText: "$device.displayName MSR: $msr", isStateChange: false)
}

def zwaveEvent(physicalgraph.zwave.commands.versionv1.VersionReport cmd) {
  def fw = "${cmd.applicationVersion}.${cmd.applicationSubVersion}"
  updateDataValue("fw", fw)
  updateDataValue("ver", "${cmd.applicationVersion >> 4}.${cmd.applicationVersion & 0xF}")
  def text = "$device.displayName: firmware version: $fw, Z-Wave version: ${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}"
  createEvent(descriptionText: text, isStateChange: false)
}

def zwaveEvent(physicalgraph.zwave.commands.configurationv1.ConfigurationReport cmd) {
  def result = []
  log.debug "ConfigurationReport: $cmd"
  
  if (!isConfigured()){
  	result << createEvent(name: "BasicReport", value: "Unconfigured", displayed: false)
    result = [result, response(zwave.configurationV1.configurationSet(parameterNumber: 0x63, configurationValue: [0xFF], size: 1))]
  } else if (cmd.parameterNumber == 0x63 && cmd.configurationValue[0] == 0) {
    result << createEvent(name: "BasicReport", value: "Off", displayed: false)
  } else {
    result << createEvent(name: "BasicReport", value: "On", displayed: false)
  }
  
  return result
}

def zwaveEvent(physicalgraph.zwave.commands.associationv2.AssociationReport cmd) {
  def result = []
  Boolean misconfigured = true
  
  if (cmd.groupingIdentifier == 1) {
    if (cmd.nodeId.any { it == zwaveHubNodeId }) {
      misconfigured = false
      setConfigured()
    }
  } else if (cmd.groupingIdentifier == 2) {
    if (cmd.nodeId.any { it == zwaveHubNodeId }) 
    {
      def string_of_assoc
      cmd.nodeId.each {
        string_of_assoc << "${it}, "
      }
      def lengthMinus2 = string_of_assoc.length() - 2
      def final_string = string_of_assoc.getAt(0..lengthMinus2)
      result << createEvent(name: "associations", value: "$final_string", descriptionText: "$device.displayName is associated in group ${cmd.groupingIdentifier} : $final_string")
      
      misconfigured = false
      setConfigured()
    }
  } else {
    result << createEvent(descriptionText: "$device.displayName lacks group $cmd.groupingIdentifier")
  }
  
  if (misconfigured) {
    unsetConfigured()
    def commands = [
      zwave.associationV1.associationSet(groupingIdentifier:1, nodeId:[1]).format(),
      zwave.associationV1.associationSet(groupingIdentifier:2, nodeId:[1]).format(),
      zwave.associationV1.associationSet(groupingIdentifier:2, nodeId:[zwaveHubNodeId]).format(),
      zwave.associationV1.associationGet(groupingIdentifier:2).format()
    ]
    result << delayBetween(commands, 1000)
  }
    
  return result
}

def refresh()
{
  log.debug "refresh() is called"

   def commands = [
    zwave.batteryV1.batteryGet().format(),
    zwave.alarmV2.AlarmGet().format(),
    zwave.associationV2.associationGet(groupingIdentifier:1).format(),
    zwave.associationV2.associationGet(groupingIdentifier:2).format()
  ]
  
  if (getDataValue("MSR") == null)
  {
    commands << zwave.manufacturerSpecificV1.manufacturerSpecificGet().format()
  }
  if (getDataValue('fw') == null)
  {
    commands << zwave.versionV1.versionGet().format()
  }
  response(delayBetween(commands, 1000))
}

def configure() {
  def result = []
  updateDataValue("getDriverVersion", getDriverVersion())
  unsetConfigured()
  
  result << response(delayBetween([
    zwave.configurationV1.configurationSet(parameterNumber: 0x63, configurationValue: [0xFF], size: 1).format(),
		zwave.associationV1.associationSet(groupingIdentifier:1, nodeId:[1]).format(),
    zwave.associationV2.associationSet(groupingIdentifier:2, nodeId:[1]).format(),
    zwave.associationV2.associationSet(groupingIdentifier:2, nodeId:[zwaveHubNodeId]).format()
  ], 1000))
  
  result << refresh()
}

def setConfigured() {
  device.updateDataValue("configured", "true")
}

def unsetConfigured() {
  device.updateDataValue("configured", "false")
}

def isConfigured() {
  Boolean configured = device.getDataValue(["configured"]) as Boolean
  return configured;
}
