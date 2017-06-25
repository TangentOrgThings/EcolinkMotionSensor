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

def getDriverVersion() {
  return "v3.05"
}

def getAssociationGroup () {
  if ( zwaveHubNodeId == 1) {
    return 1
  }
  
  return 2
}

metadata {
  definition (name: "Ecolink Motion Detector", namespace: "TangentOrgThings", author: "Brian Aker")
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
    attribute "Associated", "string"
    attribute "BasicReport", "enum", ["Unconfigured", "On", "Off"]
    attribute "MSR", "string"
    attribute "Manufacturer", "string"
    attribute "ManufacturerCode", "string"
    attribute "ProduceTypeCode", "string"
    attribute "ProductCode", "string"
    attribute "WakeUp", "string"
    attribute "firmwareVersion", "string"

    // zw:S type:2001 mfr:014A prod:0001 model:0001 ver:2.00 zwv:3.40 lib:06 cc:30,71,72,86,85,84,80,70 ccOut:20
    fingerprint mfr: "014A", prod: "0001", model: "0001", cc: "30, 71, 72, 86, 85, 84, 80, 70", ccOut: "20", deviceJoinName: "Ecolink Motion Sensor PIRZWAVE1" // Ecolink motion
    fingerprint mfr: "014A", prod: "0004", model: "0001", cc: "85, 59, 80, 30, 04, 72, 71, 73, 86, 84, 5E", ccOut: "20", deviceJoinName: "Ecolink Motion Sensor PIRZWAVE2.5-ECO"  // Ecolink motion +
  }

  simulator
  {
    status "inactive": "command: 3003, payload: 00"
    status "active": "command: 3003, payload: FF"
  }

  tiles
  {
    standardTile("motion", "device.motion", width: 2, height: 2) {
      state("active", label:'motion', icon:"st.motion.motion.active", backgroundColor:"#53a7c0")
      state("inactive", label:'no motion', icon:"st.motion.motion.inactive", backgroundColor:"#ffffff")
    }
    
    valueTile("battery", "device.battery", inactiveLabel: false, decoration: "flat") {
      state("battery", label:'${currentValue}', unit:"%")
    }
    
    valueTile("driverVersion", "device.driverVersion", inactiveLabel: true, decoration: "flat") {
      state("driverVersion", label: getDriverVersion())
	}
    
    valueTile("associated", "device.Associated", inactiveLabel: false, decoration: "flat") {
      state("device.Associated", label: '${currentValue}')
	}
    
    valueTile("tamper", "device.tamper", inactiveLabel: false, decoration: "flat") {
      state "clear", backgroundColor:"#00FF00"
      state("detected", label: "detected", backgroundColor:"#e51426")
	}
    
    standardTile("configure", "device.switch", inactiveLabel: false, decoration: "flat") {
      state "default", label:"", action:"configuration.configure", icon:"st.secondary.configure"
    }
    
    standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat") {
      state "default", label:'', action: "refresh.refresh", icon: "st.secondary.refresh"
    }

    main "motion"
    details(["motion", "battery", "tamper", "driverVersion", "configure", "associated", "refresh"])
  }
}

def parse(String description)
{
  def result = null

  if (description.startsWith("Err"))
  {
    if (description.startsWith("Err 106")) 
    {
      if (state.sec) {
        log.debug description
      } else {
        result = createEvent(
          descriptionText: "This device failed to complete the network security key exchange. If you are unable to control it via SmartThings, you must remove it from your network and add it again.",
          eventType: "ALERT",
          name: "secureInclusion",
          value: "failed",
          isStateChange: true,
        )
      }
    } else {
      result = createEvent(value: description, descriptionText: description)
    }
  } else if (description != "updated") {
    def cmd = zwave.parse(description)
	
    if (cmd) {
      result = zwaveEvent(cmd)
      
      if (!result) {
        log.warning "Parse Failed and returned ${result} for command ${cmd}"
        result = createEvent(value: description, descriptionText: description)
      }
    } else {
      log.info "Non-parsed event: ${description}"
      result = createEvent(value: description, descriptionText: description)
    }
  }
    
  return result
}

def sensorValueEvent(short value) {
  def result = []
  log.debug "sensorValueEvent: $value"
 
  if (value) {
    result << createEvent(name: "motion", value: "active", descriptionText: "$device.displayName detected motion", isStateChange: true, displayed: true)
  } else {
    result << createEvent(name: "motion", value: "inactive", descriptionText: "$device.displayName motion has stopped", isStateChange: true, displayed: true)
  }
  
  def cmds = []
  cmds.plus(setConfigured())
  if (cmds.size()) {
    result << response(commands(setConfigured(), 1000))
  }
  
  return result
}

def setConfigured() {
  def cmds = []
  
  if ( zwaveHubNodeId != 1) {
    if (! state.isConfigured) {
      cmds << zwave.configurationV1.configurationGet(parameterNumber: 0x63)
    }
  }
  
  if (! state.isAssociated ) {
    cmds << zwave.associationV2.associationGet(groupingIdentifier: 1)
    cmds << zwave.associationV2.associationGet(groupingIdentifier: getAssociationGroup())
  }
  
  if (device.currentValue("MSR") == null) {
    cmds << zwave.manufacturerSpecificV1.manufacturerSpecificGet()
  }
  
  if (device.currentValue("firmwareVersion") == null) {
    cmds << zwave.versionV1.versionGet()
  }
  
  return cmds
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {
  sensorValueEvent(cmd.value)
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd) {
  sensorValueEvent(cmd.value)
}

def zwaveEvent(physicalgraph.zwave.commands.sensorbinaryv1.SensorBinaryReport cmd) {
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

def zwaveEvent(physicalgraph.zwave.commands.wakeupv2.WakeUpNotification cmd)
{
  def result = [createEvent(descriptionText: "${device.displayName} woke up", isStateChange: false)]
  
  if (state.tamper == "clear") {
  	result << createEvent(name: "tamper", value: "clear", descriptionText: "$device.displayName is clear", isStateChange: true, displayed: true)
  }

  // If the device is in the process of configuring a newly joined network, do not send wakeUpnoMoreInformation commands
  def cmds = []
  if (isConfigured()) {
    if (!state.lastbat || (new Date().time) - state.lastbat > 53*60*60*1000) {
      cmds << zwave.batteryV1.batteryGet()
 //   } else {
//      result << response(zwave.wakeUpV1.wakeUpNoMoreInformation())
    }
  }
  
  cmds.plus(setConfigured())
  if (cmds.size())
  {
  	result << response(commands(cmds, 1000))
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
  
  if ( zwaveHubNodeId != 1) {
    if (! state.isConfigured) {
  	  result << response(commands([zwave.configurationV1.configurationSet(parameterNumber: 0x63, configurationValue: 0xFF, size: 1),
                                 zwave.batteryV1.batteryGet()
                                 ]))
    }
  }
  
  return result
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
  createEvent(descriptionText: "$device.displayName command not implemented: $cmd", displayed: true)
}

def zwaveEvent(physicalgraph.zwave.commands.manufacturerspecificv1.ManufacturerSpecificReport cmd) 
{
  def result = []
  
  def manufacturerCode = String.format("%04X", cmd.manufacturerId)
  def productTypeCode = String.format("%04X", cmd.productTypeId)
  def productCode = String.format("%04X", cmd.productId)
  
  result << createEvent(name: "ManufacturerCode", value: manufacturerCode)
  result << createEvent(name: "ProduceTypeCode", value: productTypeCode)
  result << createEvent(name: "ProductCode", value: productCode)

  def msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
  updateDataValue("MSR", msr)	updateDataValue("MSR", msr)
  updateDataValue("manufacturer", "Ecolink")
  if (!state.manufacturer) {
    state.manufacturer= "Ecolink"
  }
  
  result << createEvent([name: "MSR", value: "$msr", descriptionText: "$device.displayName", isStateChange: false])
  result << createEvent([name: "Manufacturer", value: "${state.manufacturer}", descriptionText: "$device.displayName", isStateChange: false])
  
  return result
}

def zwaveEvent(physicalgraph.zwave.commands.versionv1.VersionReport cmd) {
  def text = "$device.displayName: firmware version: ${cmd.applicationVersion}.${cmd.applicationSubVersion}, Z-Wave version: ${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}"
  createEvent([name: "firmwareVersion", value: "${cmd.applicationVersion}.${cmd.applicationSubVersion}", descriptionText: "$text", isStateChange: false])
}

def zwaveEvent(physicalgraph.zwave.commands.configurationv1.ConfigurationReport cmd) {
  def result = []
  
  if (cmd.parameterNumber == 0x63) {
    if (cmd.configurationValue == 0xFF) {
      result << createEvent(name: "BasicReport", value: "On", displayed: false)
      state.isConfigured = true
    } else if (cmd.configurationValue != 0) {
      // Attempting to be On but is misconfigured
      result << createEvent(name: "BasicReport", value: "Off", displayed: false)
      state.isConfigured = false
    } else {
      result << createEvent(name: "BasicReport", value: "Off", displayed: false)
      state.isConfigured = false
    }
  } else {
    result << createEvent(name: "BasicReport", value: "Unconfigured", displayed: false)
  }
  
  if ( zwaveHubNodeId != 1) {
    if (! state.isConfigured) {
      result << response(commands([
        zwave.configurationV1.configurationSet(parameterNumber: 0x63, configurationValue: [0xFF], size: 1),
        zwave.configurationV1.configurationGet(parameterNumber: 0x63)
      ], 1000))
    }
  }
  
  return result
}

def zwaveEvent(physicalgraph.zwave.commands.associationv2.AssociationReport cmd) {
  def result = []
  
  log.debug ("AssociationReport()")
  
  if (cmd.groupingIdentifier == getAssociationGroup()) {
    def string_of_assoc = ""
    cmd.nodeId.each {
      string_of_assoc += "${it}, "
    }
    def lengthMinus2 = string_of_assoc.length() - 3
    def final_string = string_of_assoc.getAt(0..lengthMinus2)
    
    if (cmd.nodeId.any { it == zwaveHubNodeId }) 
    {
      Boolean isStateChange = state.isAssociated ?: false
      result << createEvent(name: "Associated",
                            value: "${final_string}", 
                            descriptionText: "${final_string}",
                            displayed: true,
                            isStateChange: isStateChange)
      
      state.isAssociated = true
    } else {
      Boolean isStateChange = state.isAssociated ? true : false
      result << createEvent(name: "Associated",
                          value: "",
                          descriptionText: "${final_string}",
                          displayed: true,
                          isStateChange: isStateChange)
    }
    state.isAssociated = false
  } else {
    Boolean isStateChange = state.isAssociated ? true : false
    result << createEvent(name: "Associated",
                          value: "misconfigured",
                          descriptionText: "misconfigured group ${cmd.groupingIdentifier}",
                          displayed: true,
                          isStateChange: isStateChange)
  }
  
  if (! state.isAssociated ) {
    result << response(commands([
      zwave.associationV2.associationSet(groupingIdentifier: 1, nodeId: [1]),
      zwave.associationV2.associationSet(groupingIdentifier: getAssociationGroup(), nodeId: [zwaveHubNodeId]),
      zwave.associationV2.associationGet(groupingIdentifier: 1),
      zwave.associationV2.associationGet(groupingIdentifier: getAssociationGroup())
      ], 1000))
  }
    
  return result
}

def refresh() {
  log.debug "$device.displayName updated"
  
  state.isAssociated = false
  state.isConfigured = false
}

def updated() {
  log.debug "$device.displayName updated"
  
  state.isAssociated = false
  state.isConfigured = false
  
  sendEvent(name: "driverVersion", value: getDriverVersion(), displayed: true, isStateChange: true)
}

def installed() {
  log.debug "$device.displayName installed"
  response(commands(
    [
      zwave.manufacturerSpecificV1.manufacturerSpecificGet(),
      zwave.versionV1.versionGet(),
      zwave.associationV2.associationGet(groupingIdentifier: 1),
      zwave.associationV2.associationGet(groupingIdentifier: getAssociationGroup()),
      zwave.configurationV1.configurationGet(parameterNumber: 0x63)
   ]))
}

def configure() {
  log.debug "$device.displayName configure()"
  response(commands(
    [
      zwave.manufacturerSpecificV1.manufacturerSpecificGet(),
      zwave.versionV1.versionGet(),
      zwave.associationV2.associationGet(groupingIdentifier: 1),
      zwave.associationV2.associationGet(groupingIdentifier: getAssociationGroup()),
      zwave.configurationV1.configurationGet(parameterNumber: 0x63)
   ]))
}

private command(physicalgraph.zwave.Command cmd) {
  if (state.sec) {
    zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
  } else {
    cmd.format()
  }
}

private commands(commands, delay=200) {
  delayBetween(commands.collect{ command(it) }, delay)
}
