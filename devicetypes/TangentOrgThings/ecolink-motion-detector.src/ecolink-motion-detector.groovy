// vim :set ts=2 sw=2 sts=2 expandtab smarttab :
/**
 *  Ecolink Motion Sensor
 *
 *  Copyright 2016-2018 Brian Aker
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
  return "v4.26"
}

def isNewerModel() {
  return state.newModel
}

def isLifeLine() {
  return state.isLifeLine
}

def getAssociationGroup () {
  if ( zwaveHubNodeId == 1 || isNewerModel() || isLifeLine()) {
    return 1
  }

  return 2
}

metadata {
  definition (name: "Ecolink Motion Detector", namespace: "TangentOrgThings", author: "Brian Aker", ocfDeviceType: "x.com.st.d.sensor.motion") {
    capability "Battery"
    capability "Motion Sensor"
    capability "Refresh"
    capability "Sensor"
    capability "Tamper Alert"
    capability "Temperature Measurement"
    
    attribute "DeviceReset", "enum", ["false", "true"]
    attribute "logMessage", "string"        // Important log messages.
    attribute "lastError", "string"        // Last Error  messages.

    // String attribute with name "firmwareVersion"
    attribute "firmwareVersion", "string"
    attribute "driverVersion", "string"
    attribute "Lifeline", "string"
    attribute "Repeated", "string"
    attribute "BasicReport", "enum", ["Unconfigured", "On", "Off"]
    attribute "MSR", "string"
    attribute "Manufacturer", "string"
    attribute "ManufacturerCode", "string"
    attribute "ProduceTypeCode", "string"
    attribute "ProductCode", "string"
    attribute "WakeUp", "string"
    attribute "firmwareVersion", "string"
    attribute "LastActive", "string"
    attribute "isLifeline", "enum", ["false", "true"]

    attribute "NIF", "string"

    // zw:S type:2001 mfr:014A prod:0001 model:0001 ver:2.00 zwv:3.40 lib:06 cc:30,71,72,86,85,84,80,70 ccOut:20
    fingerprint type: "2001", mfr: "014A", prod: "0001", model: "0001", deviceJoinName: "Ecolink Motion Sensor PIRZWAVE1" // Ecolink motion //, cc: "30, 71, 72, 86, 85, 84, 80, 70", ccOut: "20"
    fingerprint type: "2001", mfr: "014A", prod: "0004", model: "0001", deviceJoinName: "Ecolink Motion Sensor PIRZWAVE2.5-ECO"  // Ecolink motion + // , cc: "85, 59, 80, 30, 04, 72, 71, 73, 86, 84, 5E", ccOut: "20"
  }

  simulator {
    status "inactive": "command: 3003, payload: 00"
    status "active": "command: 3003, payload: FF"
  }

  preferences {
    input "newModel", "bool", title: "Newer model", description: "... ", required: false
  }

  tiles {
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

    valueTile("temperature", "device.temperature", width: 2, height: 2) {
      state("temperature", label:'${currentValue}', unit:"dF",
      backgroundColors:[
      [value: 31, color: "#153591"],
      [value: 44, color: "#1e9cbb"],
      [value: 59, color: "#90d2a7"],
      [value: 74, color: "#44b621"],
      [value: 84, color: "#f1d801"],
      [value: 95, color: "#d04e00"],
      [value: 96, color: "#bc2323"]
      ]
      )
    }

    valueTile("tamper", "device.tamper", inactiveLabel: false, decoration: "flat") {
      state "clear", backgroundColor:"#00FF00"
      state("detected", label: "detected", backgroundColor:"#e51426")
    }

    standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat") {
      state "default", label:'', action: "refresh.refresh", icon: "st.secondary.refresh"
    }

    valueTile("lastActive", "device.LastActive", width:2, height:2, inactiveLabel: true, decoration: "flat") {
      state "default", label: '${currentValue}'
    }
    
    standardTile("reset", "device.DeviceReset", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
      state "false", label:'', backgroundColor:"#ffffff"
      state "true", label:'reset', backgroundColor:"#e51426"
    }

    main "motion"
    details(["motion", "battery", "tamper", "driverVersion", "temperature", "associated", "lastActive", "reset", "refresh"])
  }
}

def parse(String description) {
  def result = null

  if (description.startsWith("Err")) {
    if (description.startsWith("Err 106")) {
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

      if (! result) {
        log.warning "zwaveEvent() no result for command ${cmd}"
        result = createEvent(value: description, descriptionText: "zwaveEvent() no result for command ${cmd}" )
      }
    } else {
      log.info "Non-parsed event: ${description}"
      result = createEvent(value: description, descriptionText: description)
    }
  } else {
    log.debug "Silent event: ${description}"
    result = createEvent(value: description, descriptionText: description)
  }

  return result
}

def followupStateCheck() {
  log.debug("$device.displayName followupStateCheck")
  
  def now = new Date().time
  def last = state.lastActive + 300
  
  log.debug("$device.displayName ... $last < $now")
  if (state.lastActive + 300 < now) {
    sendEvent(name: "motion", value: "inactive", descriptionText: "$device.displayName reset on followupStateCheck", isStateChange: true, displayed: true)
  }
}

def sensorValueEvent(Boolean happening) {
  log.debug "sensorValueEvent() $happening"
  def result = []

  if (happening) {
    state.lastActive = new Date().time
    sendEvent(name: "LastActive", value: state.lastActive, displayed: false)
    
    runIn(360, followupStateCheck)
  }
    
  def cmds = setConfigured()

  result << createEvent(name: "motion", value: happening ? "motion" : "inactive", descriptionText: "$device.displayName motion has stopped", isStateChange: true, displayed: true)
 
  if (cmds.size()) {
    result << response(delayBetween(cmds, 1000))
  }
  
  log.debug "sensorValueEvent() RESP: $result"
    
  return result
}

def setConfigured() {
  def cmds = []

  if ( zwaveHubNodeId != 1 && ! state.newModel) {
    if (! state.isConfigured) {
      cmds << zwave.configurationV1.configurationGet(parameterNumber: 0x63).format()
    }
  }

  if (! state.isAssociated ) {
    cmds << zwave.associationV2.associationGet(groupingIdentifier: 1).format()
      cmds << zwave.associationV2.associationGet(groupingIdentifier: 2).format()
  }

  if (device.currentValue("MSR") == null) {
    cmds << zwave.manufacturerSpecificV1.manufacturerSpecificGet().format()
  }

  if (device.currentValue("firmwareVersion") == null) {
    cmds << zwave.versionV1.versionGet().format()
  }

  return cmds
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {
  log.debug("$device.displayName $cmd")
  
  if (! isLifeLine()) {
    sensorValueEvent((Boolean)cmd.value)
  } else {
    createEvent(descriptionText: "duplicate event", isStateChange: false, displayed: false)
  }
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd) {
  log.debug("$device.displayName $cmd")
  
  if (! isLifeLine()) {
    sensorValueEvent((Boolean)cmd.value)
  } else {
    createEvent(descriptionText: "duplicate event", isStateChange: false, displayed: false)
  }
}

def zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
  log.debug("$device.displayName $cmd")
  
  if (! isLifeLine()) {
    sensorValueEvent((Boolean)cmd.value)
  } else {
    createEvent(descriptionText: "duplicate event", isStateChange: false, displayed: false)
  }
}

def zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinarySet cmd) {
  log.debug("$device.displayName $cmd")

  if (! isLifeLine()) {
    sensorValueEvent((Boolean)cmd.sensorValue)
  } else {
    createEvent(descriptionText: "duplicate event", isStateChange: false, displayed: false)
  }
}

def zwaveEvent(physicalgraph.zwave.commands.sensorbinaryv2.SensorBinaryReport cmd) {
  log.debug("$device.displayName $cmd")
  sensorValueEvent((Boolean)cmd.sensorValue)
}

// Older devices
def zwaveEvent(physicalgraph.zwave.commands.alarmv2.AlarmReport cmd) {
  log.debug("$device.displayName $cmd")
  def result = []

  if (cmd.alarmLevel == 0x11) {
    result << createEvent(name: "tamper", value: "detected", descriptionText: "$device.displayName covering was removed", isStateChange: true, displayed: true)
  } else {
    result << createEvent(name: "tamper", value: "clear", descriptionText: "$device.displayName is clear", isStateChange: true, displayed: true)
  }

  return result
}

// NotificationReport() NotificationReport(event: 0, eventParameter: [], eventParametersLength: 0, notificationStatus: 255, notificationType: 7, reserved61: 0, sequence: false, v1AlarmLevel: 0, v1AlarmType: 0, zensorNetSourceNodeId: 0)
def zwaveEvent(physicalgraph.zwave.commands.notificationv3.NotificationReport cmd) {
  log.debug("$device.displayName $cmd")

  def result = []

  if (cmd.notificationType == 7) {
    Boolean current_status = cmd.notificationStatus == 255 ? true : false
    
    switch (cmd.event) {
      case 3:
        sendEvent(name: "tamper", value: current_status ? "detected" : "clear", descriptionText: "$device.displayName covering was removed", isStateChange: true, displayed: true)
        result << createEvent(name: "tamper", value: current_status ? "detected" : "clear", descriptionText: "$device.displayName covering was removed", isStateChange: true, displayed: true)
        break;
      case 8:
        result << sensorValueEvent(true)
        break;
      case 0:
        result << sensorValueEvent(false)
        break;
      case 2:
        result << sensorValueEvent(current_status)
        break;
      default:
        result << createEvent(descriptionText: "$device.displayName unknown event for notification 7: $cmd", isStateChange: true)
        log.error "Unknown state: $cmd"
    }
  } else if (cmd.notificationType == 8) {
    result << createEvent(descriptionText: "$device.displayName unknown notificationType: $cmd", isStateChange: true)
    log.error "Unknown state for notificationType 8: $cmd"
  } else if (cmd.notificationType) {
    def text = "Unknown state for notificationType: $cmd"
    result << createEvent(name: "notification$cmd.notificationType", value: "$text", descriptionText: text, isStateChange: true, displayed: true)
    log.error "Unknown notificationType: $cmd"
  } else {
    def value = cmd.v1AlarmLevel == 255 ? "active" : cmd.v1AlarmLevel ?: "inactive"
    result << createEvent(name: "alarm $cmd.v1AlarmType", value: value, isStateChange: true, displayed: true)
  }

  return result
}

def zwaveEvent(physicalgraph.zwave.commands.applicationstatusv1.ApplicationRejectedRequest cmd) {
  log.debug("$device.displayName $cmd")
  [:]
}

def zwaveEvent(physicalgraph.zwave.commands.applicationcapabilityv1.CommandCommandClassNotSupported cmd) {
  log.debug("$device.displayName $cmd")
  [:]
}

/* 
def zwaveEvent(physicalgraph.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd) {
  log.debug("$device.displayName $cmd")
  
  if (cmd.sensorType == 1) {
    [ createEvent(name: "temperature", value: cmd.scaledSensorValue, unit:"dF", isStateChange: true, displayed: true) ]
  } else if (cmd.sensorType == 7) {
    Boolean current_status = cmd.notificationStatus == 255 ? true : false
    [ sensorValueEvent(current_status) ]
  } else {
    [ createEvent(descriptionText: "$cmd", isStateChange: true, displayed: true) ]
  }
}
*/

def zwaveEvent(physicalgraph.zwave.commands.wakeupv2.WakeUpNotification cmd) {
  log.debug("$device.displayName $cmd")
  def result = [createEvent(descriptionText: "${device.displayName} woke up", isStateChange: false)]

  if (state.tamper == "clear") {
    result << createEvent(name: "tamper", value: "clear", descriptionText: "$device.displayName is clear", isStateChange: true, displayed: true)
  }

  // If the device is in the process of configuring a newly joined network, do not send wakeUpnoMoreInformation commands
  /*
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
   */

  return result
}


def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) {
  log.debug("$device.displayName $cmd")

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

  def result = [ createEvent(map) ]

  /*
  if ( zwaveHubNodeId != 1) {
  if (! state.isConfigured) {
  result << response(commands([zwave.configurationV1.configurationSet(parameterNumber: 0x63, configurationValue: 0xFF, size: 1),
  zwave.batteryV1.batteryGet()
  ]))
  }
  }
   */

  return result
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
  log.error("$device.displayName command not implemented: $cmd")
  [ createEvent(descriptionText: "$device.displayName command not implemented: $cmd", displayed: true) ]
}

def zwaveEvent(physicalgraph.zwave.commands.deviceresetlocallyv1.DeviceResetLocallyNotification cmd) {
  logger("$device.displayName $cmd")
  state.DeviceReset = true
  [ createEvent(name: "DeviceReset", value: state.reset, descriptionText: cmd.toString(), isStateChange: true, displayed: true) ]
}

def zwaveEvent(physicalgraph.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
  log.debug("$device.displayName $cmd")
  
  def result = []
  def manufacturerCode = String.format("%04X", cmd.manufacturerId)
  def productTypeCode = String.format("%04X", cmd.productTypeId)
  def productCode = String.format("%04X", cmd.productId)

  sendEvent(name: "ManufacturerCode", value: manufacturerCode)
  sendEvent(name: "ProduceTypeCode", value: productTypeCode)
  sendEvent(name: "ProductCode", value: productCode)

  state.manufacturer = cmd.manufacturer ? cmd.manufacturer : "Ecolink"

  def msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
  updateDataValue("MSR", msr)	updateDataValue("MSR", msr)
  updateDataValue("manufacturer", state.manufacturer)
  sendEvent(name: "Manufacturer", value: state.manufacturer, descriptionText: "$device.displayName", isStateChange: false)

  state.newModel = cmd.productId == 4 ? true : false

  sendEvent(name: "MSR", value: msr, descriptionText: "$device.displayName", isStateChange: false)
  [ createEvent(name: "Manufacturer", value: state.manufacturer, descriptionText: "$device.displayName", isStateChange: false) ]
}

def zwaveEvent(physicalgraph.zwave.commands.versionv1.VersionReport cmd) {
  log.debug("$device.displayName $cmd")
  def text = "$device.displayName: firmware version: ${cmd.applicationVersion}.${cmd.applicationSubVersion}, Z-Wave version: ${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}"
  [ createEvent(name: "firmwareVersion", value: "${cmd.applicationVersion}.${cmd.applicationSubVersion}", descriptionText: "$text", isStateChange: false) ]
}

def zwaveEvent(physicalgraph.zwave.commands.configurationv2.ConfigurationReport cmd) {
  log.debug("$device.displayName $cmd")

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

  if ( zwaveHubNodeId != 1 && ! state.newModel && device.currentValue("MSR")) {
    if (! state.isConfigured) {
      result << response(delayBetween([
        zwave.configurationV1.configurationSet(parameterNumber: 0x63, configurationValue: [0xFF], size: 1).format(),
        zwave.configurationV1.configurationGet(parameterNumber: 0x63).format(),
      ], 1000))
    }
  }

  return result
}

def zwaveEvent(physicalgraph.zwave.commands.associationv2.AssociationReport cmd) {
  log.debug("$device.displayName $cmd")

  def result = []
  def cmds = []

  String final_string

  if (cmd.nodeId) {
    def string_of_assoc = ""
    cmd.nodeId.each {
      string_of_assoc += "${it}, "
    }
    def lengthMinus2 = string_of_assoc.length() - 3
    final_string = string_of_assoc.getAt(0..lengthMinus2)
  }

  if (cmd.groupingIdentifier == 0x01) { // Lifeline
    if (cmd.nodeId.any { it == zwaveHubNodeId }) {
      Boolean isStateChange = state.isAssociated ?: false
      state.isLifeLine = true
      result << createEvent(name: "Lifeline",
          value: "${final_string}", 
          descriptionText: "${final_string}",
          displayed: true,
          isStateChange: isStateChange)

      state.isAssociated = true
    } else {
      Boolean isStateChange = state.isAssociated ? true : false
      result << createEvent(name: "Lifeline",
          value: "",
          descriptionText: "${final_string}",
          displayed: true,
          isStateChange: isStateChange)
      if (isLifeLine()) {
        state.isAssociated = false
      }
    }
  } else if (cmd.groupingIdentifier == 0x02) { // Repeated
    if (cmd.nodeId.any { it == zwaveHubNodeId }) {
      Boolean isStateChange = state.isAssociated ?: false
      result << createEvent(name: "Repeated",
      value: "${final_string}", 
      displayed: true,
      isStateChange: isStateChange)
      if (isLifeLine()) {
        cmds << zwave.associationV2.AssociationRemove(groupingIdentifier: cmd.groupingIdentifier, nodeId: [zwaveHubNodeId]).format()
      } else {
        state.isAssociated = true
      }
    } else {
      Boolean isStateChange = state.isAssociated ? true : false
      result << createEvent(name: "Repeated",
      descriptionText: "${final_string}",
      displayed: true,
      isStateChange: isStateChange)
    }
  } else {
    result << createEvent(descriptionText: "$device.displayName unknown association: $cmd", isStateChange: true, displayed: true)
    // Error 
  }

  if (! state.isAssociated ) {
    cmds << zwave.associationV2.associationSet(groupingIdentifier: getAssociationGroup(), nodeId: [zwaveHubNodeId]).format()
    cmds << zwave.associationV2.associationGet(groupingIdentifier: getAssociationGroup()).format()
  }
  
  if (cmds.size()) {
    result << response(delayBetween(cmds, 500))
  }

  return result
}

def refresh() {
  log.debug "$device.displayName refresh"

  state.isAssociated = false
  state.isConfigured = false
}

def zwaveEvent(physicalgraph.zwave.commands.zwavecmdclassv1.NodeInfo cmd) {
  log.debug("$device.displayName $cmd")
  [ createEvent(name: "NIF", value: "$cmd", descriptionText: "$cmd", isStateChange: true, displayed: true) ]
}

def prepDevice() {
  [
    zwave.zwaveCmdClassV1.requestNodeInfo(),
    zwave.manufacturerSpecificV1.manufacturerSpecificGet(),
    zwave.versionV1.versionGet(),
    zwave.associationV2.associationGet(groupingIdentifier: 1),
    zwave.associationV2.associationGet(groupingIdentifier: getAssociationGroup()),
    // zwave.configurationV1.configurationGet(parameterNumber: 0x63),
  ]
}

def updated() {
  log.debug "$device.displayName updated()"
  state.loggingLevelIDE = 4
  
  if (0) {
    def zwInfo = getZwaveInfo()
    if ($zwInfo) {
      log.debug("$device.displayName $zwInfo")
      sendEvent(name: "NIF", value: "$zwInfo", isStateChange: true, displayed: true)
    }
  }

  sendEvent(name: "tamper", value: "clear")
  
  state.isAssociated = false
  state.isConfigured = false
  sendEvent(name: "driverVersion", value: getDriverVersion(), displayed: true, isStateChange: true)
  // sendEvent(name: "motion", value: "inactive", descriptionText: "$device.displayName reset on update", isStateChange: true, displayed: true)
  sendCommands(prepDevice())
}

def installed() {
  log.debug "$device.displayName installed()"
  state.loggingLevelIDE = 4
  
  if (0) {
    def zwInfo = getZwaveInfo()
    if ($zwInfo) {
      log.debug("$device.displayName $zwInfo")
      sendEvent(name: "NIF", value: "$zwInfo", isStateChange: true, displayed: true)
    }
  }

  sendEvent(name: "driverVersion", value: getDriverVersion(), displayed: true, isStateChange: true)
  sendCommands(prepDevice())
}

/*****************************************************************************************************************
 *  Private Helper Functions:
 *****************************************************************************************************************/

/**
 *  encapCommand(cmd)
 *
 *  Applies security or CRC16 encapsulation to a command as needed.
 *  Returns a physicalgraph.zwave.Command.
 **/
private encapCommand(physicalgraph.zwave.Command cmd) {
  if (state.sec) {
    return zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd)
  }
  else if (state.useCrc16) {
    return zwave.crc16EncapV1.crc16Encap().encapsulate(cmd)
  }
  else {
    return cmd
  }
}

/**
 *  prepCommands(cmds, delay=200)
 *
 *  Converts a list of commands (and delays) into a HubMultiAction object, suitable for returning via parse().
 *  Uses encapCommand() to apply security or CRC16 encapsulation as needed.
 **/
private prepCommands(cmds, delay=200) {
  return response(delayBetween(cmds.collect{ (it instanceof physicalgraph.zwave.Command ) ? encapCommand(it).format() : it }, delay))
}

/**
 *  sendCommands(cmds, delay=200)
 *
 *  Sends a list of commands directly to the device using sendHubCommand.
 *  Uses encapCommand() to apply security or CRC16 encapsulation as needed.
 **/
private sendCommands(cmds, delay=200) {
  sendHubCommand( cmds.collect{ (it instanceof physicalgraph.zwave.Command ) ? response(encapCommand(it)) : response(it) }, delay)
}

/**
 *  logger()
 *
 *  Wrapper function for all logging:
 *    Logs messages to the IDE (Live Logging), and also keeps a historical log of critical error and warning
 *    messages by sending events for the device's logMessage attribute.
 *    Configured using configLoggingLevelIDE and configLoggingLevelDevice preferences.
 **/
private logger(msg, level = "debug") {
  switch(level) {
    case "error":
    if (state.loggingLevelIDE >= 1) {
      log.error msg
    }
    if (state.loggingLevelDevice >= 1) {
      sendEvent(name: "lastError", value: "ERROR: ${msg}", displayed: true, isStateChange: true)
    }
    break

    case "warn":
    if (state.loggingLevelIDE >= 2) {
      log.warn msg
    }
    if (state.loggingLevelDevice >= 2) {
      sendEvent(name: "logMessage", value: "WARNING: ${msg}", displayed: false, isStateChange: true)
    }
    break

    case "info":
    if (state.loggingLevelIDE >= 3) {
      log.info msg
    }
    break

    case "debug":
    if (state.loggingLevelIDE >= 4)
    {
      log.debug msg
    }
    break

    case "trace":
    if (state.loggingLevelIDE >= 5) {
      log.trace msg
    }
    break

    default:
    log.debug msg
    break
  }
}
